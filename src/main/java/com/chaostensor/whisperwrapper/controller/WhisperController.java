package com.chaostensor.whisperwrapper.controller;


import com.chaostensor.whisperwrapper.dto.WhisperRequest;
import com.chaostensor.whisperwrapper.dto.WhisperResponse;
import com.chaostensor.whisperwrapper.dto.WhisperUploadRequest;
import com.chaostensor.whisperwrapper.dto.WhisperStatus;
import com.chaostensor.whisperwrapper.dto.CompletedStatus;
import com.chaostensor.whisperwrapper.dto.WhisperCollectionResponse;
import com.chaostensor.whisperwrapper.dto.PendingStatus;
import com.chaostensor.whisperwrapper.entity.WhisperJob;
import com.chaostensor.whisperwrapper.repository.WhisperJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * TODO: there is some duplication between the two controllers, extract that.
 * <p>
 * TODO NOTE I'm hosting both options in the same service at the moment.
 *   This means the same model may be loaded twice.
 *   And actually that may even be true for multiple requests to the same runner. It's not running as a service
 *   from what I can tell.
 *   We can split these into separate sidecars.
 *   But we may also need to work out how to ensure that the model remains loaded and is shared across cli invocations.
 */
@RestController
@RequestMapping("/whispers")
public class WhisperController {

    /**
     * Mount your docker external path here. Unless just running locally via spring boot. In which case configure this via env
     * var to be wherever your input files will be.
     */
    @Value("${app.media-input}")
    String mediaBasePath;

    /**
     * Base path for transcript output files.
     */
    @Value("${app.transcript-output}")
    String transcriptOutputBasePath;

    /**
     * Base path for completed video files.
     */
    @Value("${app.video-output}")
    String videoOutputBasePath;

    private final WhisperJobRepository whisperJobRepository;


    public WhisperController(WhisperJobRepository whisperJobRepository) {
        this.whisperJobRepository = whisperJobRepository;
    }

    /**
     * NOTe this needs to give you a job id, that you can query for later.
     * <p>
     * <p>
     * TODO: ensure we support all the input args of the wrapped processor
     *
     */
    @PostMapping
    public Mono<ResponseEntity<WhisperResponse>> create(@RequestBody WhisperRequest request) {

        final UUID jobId = UUID.randomUUID();

        Path filePath = Paths.get(mediaBasePath).resolve(request.getFileName());

        return computeHashAndCheckExists(filePath)
                .flatMap(hashAndExists -> {
                    if (hashAndExists.exists()) {
                        return Mono.error(new DuplicateRequestException());
                    }
                    return createAndStartJob(jobId, hashAndExists.hash(), request.getFileName(), request);
                });

    }

    /**
     * Alternative version of createJob that accepts a video file upload.
     * Saves the uploaded file to mediaBasePath and kicks off the whisper job.
     * Uses hash of file content + UUID as filename, checks for duplicates.
     */
    @PostMapping("/upload")
    public Mono<ResponseEntity<WhisperResponse>> createFromUpload(@ModelAttribute WhisperUploadRequest uploadRequest) {

        final UUID jobId = UUID.randomUUID();

        MultipartFile file = uploadRequest.getFile();

        return computeHashCheckExistsAndSaveFile(file, jobId)
                .flatMap(hashAndFilename -> {
                    if (hashAndFilename.filename() == null) {
                        return Mono.error(new DuplicateRequestException());
                    }

                    // Create WhisperRequest with the saved file path (relative to mediaBasePath)
                    WhisperRequest request = WhisperRequest.builder()
                            .fileName(hashAndFilename.filename())
                            .task(uploadRequest.getTask())
                            .language(uploadRequest.getLanguage())
                            .timestamp(uploadRequest.getTimestamp())
                            .numSpeakers(uploadRequest.getNumSpeakers())
                            .minSpeakers(uploadRequest.getMinSpeakers())
                            .maxSpeakers(uploadRequest.getMaxSpeakers())
                            .build();

                    return createAndStartJob(jobId, hashAndFilename.hash(), hashAndFilename.filename(), request);
                });
    }

    @GetMapping("/{jobId}")
    public Mono<ResponseEntity<WhisperResponse>> get(@PathVariable String jobId) {
        try {
            UUID uuid = UUID.fromString(jobId);
            return whisperJobRepository.findById(uuid)
                    .flatMap(job -> {
                        WhisperStatus status;
                        if ("completed".equals(job.getStatus())) {
                            status = new CompletedStatus("completed", job.getTranscriptText());
                        } else {
                            status = new PendingStatus("pending");
                        }
                        WhisperResponse response = WhisperResponse.builder()
                                .jobId(jobId)
                                .status(status)
                                .build();
                        return Mono.just(ResponseEntity.ok(response));
                    })
                    .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.internalServerError().build());
        }
    }

    @GetMapping("")
    public Mono<ResponseEntity<WhisperCollectionResponse>> list() {
        return whisperJobRepository.findAll()
                .map(job -> job.getId().toString())
                .collectList()
                .map(jobIds -> {
                    WhisperCollectionResponse response = new WhisperCollectionResponse(jobIds);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Computes SHA-256 hash of a file using streaming approach.
     */
    private Mono<String> computeFileHash(Path filePath) {
        return Mono.fromCallable(() -> {
            try (var inputStream = Files.newInputStream(filePath)) {
                return computeHashFromInputStream(inputStream);
            }
        });
    }

    private Mono<String> computeFileHash(MultipartFile file) {
        return Mono.fromCallable(() -> {
            try (var inputStream = file.getInputStream()) {
                return computeHashFromInputStream(inputStream);
            }
        });
    }

    private String computeHashFromInputStream(InputStream inputStream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        byte[] hashBytes = digest.digest();
        return bytesToHex(hashBytes);
    }

    private Mono<Boolean> checkHashExists(String hash) {
        return whisperJobRepository.findByHash(hash).hasElement();
    }

    private Mono<Void> saveFile(MultipartFile file, String filename) {
        Path mediaPath = Paths.get(mediaBasePath);
        Path targetPath = mediaPath.resolve(filename);
        return Mono.fromCallable(() -> {
            Files.createDirectories(mediaPath);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return null;
        });
    }

    private Mono<WhisperJob> createJob(UUID jobId, String hash, String filename) {
        WhisperJob job = new WhisperJob(jobId, hash, "pending", null, filename);
        return whisperJobRepository.save(job);
    }

    private void startJob(WhisperJob job, WhisperRequest request) {
        processJobAsync(job, request, job.getId()).subscribe();
    }

    private Mono<HashAndExists> computeHashAndCheckExists(Path filePath) {
        return computeFileHash(filePath)
                .flatMap(hash -> checkHashExists(hash)
                        .map(exists -> new HashAndExists(hash, exists)));
    }

    private Mono<HashAndFilename> computeHashCheckExistsAndSaveFile(MultipartFile file, UUID jobId) {
        return computeFileHash(file)
                .flatMap(hash -> checkHashExists(hash)
                        .flatMap(exists -> {
                            if (exists) {
                                return Mono.just(new HashAndFilename(hash, null));
                            } else {
                                String uniqueFilename = jobId.toString();
                                return saveFile(file, uniqueFilename)
                                        .then(Mono.just(new HashAndFilename(hash, uniqueFilename)));
                            }
                        }));
    }

    private Mono<ResponseEntity<WhisperResponse>> createAndStartJob(UUID jobId, String hash, String filename, WhisperRequest request) {
        return createJob(jobId, hash, filename)
                .doOnSuccess(job -> startJob(job, request))
                .thenReturn(ResponseEntity.ok(
                        WhisperResponse.builder()
                                .jobId(jobId.toString()).build()
                ));
    }


    private Mono<Void> processJobAsync(WhisperJob job, WhisperRequest request, UUID jobId) {
        return Mono.fromCallable(() -> {
                    kickOffWhisperJob(request, jobId);
                    return (Void) null;
                })
                .then(Mono.fromCallable(() -> {
                    String transcript = Files.readString(Paths.get(transcriptOutputBasePath).resolve(jobId.toString()));
                    job.setStatus("completed");
                    job.setTranscriptText(transcript);
                    return job;
                }))
                .flatMap(whisperJobRepository::save)
                .then(Mono.fromCallable(() -> {
                    Path source = Paths.get(mediaBasePath).resolve(request.getFileName());
                    Path dest = Paths.get(videoOutputBasePath).resolve(request.getFileName());
                    Files.createDirectories(dest.getParent());
                    Files.move(source, dest);
                    return (Void) null;
                }))
                .doOnError(e -> {
                    job.setStatus("failed");
                    whisperJobRepository.save(job).subscribe(); // fire and forget
                });
    }

    private void kickOffWhisperJob(final WhisperRequest request, final UUID jobId) {
        final Process process;
        try {
            List<String> command = new ArrayList<>();
            command.add("insanely-fast-whisper");
            command.add("--file-name");
            // TODO ensure this can't result in directory traversal.
            // TODO ensure user has access rights to read and transcribe the video. Future task for if we ever make
            //  this wrapper more standalone
            command.add(Paths.get(mediaBasePath).resolve(request.getFileName()).normalize().toString());

            // TODO Not likely needed for our use case or something the client would know, or that we would want them to know
            //if (request.getDeviceId() != null && !request.getDeviceId().isEmpty()) {
            //    command.add("--device-id");
            //    command.add(request.getDeviceId());
            //}

            // Generate UUID-based transcript path relative to the configured base path
            String transcriptPath = Paths.get(transcriptOutputBasePath).resolve(jobId.toString()).toString();
            command.add("--transcript-path");
            command.add(transcriptPath);

            // External process should not be able to give us their hf tokens or
            // trigger download of a model we don't already support.
            // TODO see if we can support model selection while still banning
            // automatic download if the model is not already available.
            // if (request.getModelName() != null && !request.getModelName().isEmpty()) {
            //     command.add("--model-name");
            //     command.add(request.getModelName());
            // }
            if (request.getTask() != null && !request.getTask().isEmpty()) {
                command.add("--task");
                command.add(request.getTask());
            }
            if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
                command.add("--language");
                command.add(request.getLanguage());
            }
            // this is something we need to have control over
            //if (request.getBatchSize() != null) {
            //    command.add("--batch-size");
            //    command.add(request.getBatchSize().toString());
            //}
            // TODO: Not currently installed int he env
            //if (request.getFlash() != null && request.getFlash()) {
            //    command.add("--flash");
            //   command.add("True");
            //}
            if (request.getTimestamp() != null && !request.getTimestamp().isEmpty()) {
                command.add("--timestamp");
                command.add(request.getTimestamp());
            }
            // External process should not be able to give us their hf tokens or
            // trigger download of a model we don't already support.
            //if (request.getHfToken() != null && !request.getHfToken().isEmpty()) {
            //    command.add("--hf-token");
            //    command.add(request.getHfToken());
            //}
            // External process should not be able to give us their hf tokens or
            // trigger download of a model we don't already support.
            // TODO see if we can support model selection while still banning
            // automatic download if the model is not already available.
            //if (request.getDiarizationModel() != null && !request.getDiarizationModel().isEmpty()) {
            //    command.add("--diarization_model");
            //    command.add(request.getDiarizationModel());
            //}
            if (request.getNumSpeakers() != null) {
                command.add("--num-speakers");
                command.add(request.getNumSpeakers().toString());
            }
            if (request.getMinSpeakers() != null) {
                command.add("--min-speakers");
                command.add(request.getMinSpeakers().toString());
            }
            if (request.getMaxSpeakers() != null) {
                command.add("--max-speakers");
                command.add(request.getMaxSpeakers().toString());
            }

            process = Runtime.getRuntime().exec(command.toArray(new String[0]));
        } catch (IOException e) {
            throw new RuntimeException("failed to initialize the process", e);
        }

        final BufferedReader solveOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        final BufferedReader solveErrors = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        solveOutput.lines().forEach(System.out::println);

        try {

            /**
             * TODO no idea how long these take.
             *
             * It can vary by model and runner and media length.
             *
             * We may have to allow the timeout to be a request param
             *
             * But as of now , insanely fast reports processing times of less than 30 minutes ( some times less than 2 minutes ) for 150 min of audio.
             *
             * But that should be the faster offering.
             *
             * Setting to 4 hours for now.... which is perhaps too long to be useful. But we'll see.
             */
            process.onExit().get(4, TimeUnit.HOURS);
        } catch (TimeoutException te) {

            try {
                process.destroy();
            } catch (RuntimeException processTerminateFailure) {
                te.addSuppressed(processTerminateFailure);
            }

            throw new RuntimeException("Process failed to complete in time: " + solveErrors.lines().collect(Collectors.joining()), te);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(solveErrors.lines().collect(Collectors.joining()), e);
        }
        if (process.exitValue() != 0) {

            /*
             * TODO we need to save this with the job id so that the failure result can be handed to the client when it polls
             */
            throw new RuntimeException("Process failed: " + solveErrors.lines().collect(Collectors.joining()));
        }
    }

    // Record classes for flattening reactive chains
    private record HashAndExists(String hash, boolean exists) {
    }

    private record HashAndFilename(String hash, String filename) {
    }

}