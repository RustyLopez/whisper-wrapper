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
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
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
 *
 * TODO NOTE I'm hosting both options in the same service at the moment.
 *   This means the same model may be loaded twice.
 *   And actually that may even be true for multiple requests to the same runner. It's not running as a service
 *   from what I can tell.
 *   We can split these into separate sidecars.
 *   But we may also need to work out how to ensure that the model remains loaded and is shared across cli invocations.
 */
@RestController
@RequestMapping("/insanely-fast-whisper")
public class InsanelyFastWhisperController {

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
    @Value("${app.video-has-been-transcribed}")
    String videoOutputBasePath;

    private final WhisperJobRepository whisperJobRepository;


    public InsanelyFastWhisperController(WhisperJobRepository whisperJobRepository) {
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
    public Mono<ResponseEntity<WhisperResponse>> createJob(@RequestBody WhisperRequest request) {

        final UUID jobId = UUID.randomUUID();

        Path filePath = Paths.get(mediaBasePath).resolve(request.getFileName());

        return computeFileHash(filePath)
                .flatMap(hash -> {
                    // Save job to DB with computed hash
                    WhisperJob job = new WhisperJob(jobId, hash, "pending", null, request.getFileName());

                    // Kick off the whisper job asynchronously
                    return whisperJobRepository.save(job)
                            .doOnSuccess(savedJob -> processJobAsync(savedJob, request, jobId).subscribe())
                            .thenReturn(ResponseEntity.ok(
                                    WhisperResponse.builder()
                                            .jobId(jobId.toString()).build()
                            ));
                });

    }

    /**
     * Alternative version of createJob that accepts a video file upload.
     * Saves the uploaded file to mediaBasePath and kicks off the whisper job.
     * Uses hash of file content + UUID as filename, checks for duplicates.
     */
    @PostMapping("/upload")
    public Mono<ResponseEntity<WhisperResponse>> createJobFromUpload(@ModelAttribute WhisperUploadRequest uploadRequest) {

        final UUID jobId = UUID.randomUUID();

        MultipartFile file = uploadRequest.getFile();

        return Mono.fromCallable(() -> {
            // Compute SHA-256 hash of the file using streaming
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var inputStream = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        })
        .<ResponseEntity<WhisperResponse>>flatMap(hash -> {
            Path mediaPath = Paths.get(mediaBasePath);
            return Mono.fromCallable(() -> {
                Files.createDirectories(mediaPath); // ensure directory exists
                try (var paths = Files.list(mediaPath)) {
                    return paths.anyMatch(path -> path.getFileName().toString().startsWith(hash));
                }
            })
            .flatMap(isDuplicate -> {
                if (isDuplicate) {
                    return Mono.error(DuplicateRequestException::new);
                }

                // Generate filename: UUID only (ignore provided name)
                String uniqueFilename = jobId.toString();
                Path targetPath = mediaPath.resolve(uniqueFilename);

                return Mono.fromCallable(() -> {
                    // Copy the file
                    Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                    return uniqueFilename;
                })
                .<ResponseEntity<WhisperResponse>>flatMap(filename -> {
                    // Save job to DB
                    WhisperJob job = new WhisperJob(jobId, hash, "pending", null, filename);
                    return whisperJobRepository.save(job)
                        .flatMap(savedJob -> {
                            // Create WhisperRequest with the saved file path (relative to mediaBasePath)
                            WhisperRequest request = WhisperRequest.builder()
                                    .fileName(filename)
                                    .task(uploadRequest.getTask())
                                    .language(uploadRequest.getLanguage())
                                    .timestamp(uploadRequest.getTimestamp())
                                    .numSpeakers(uploadRequest.getNumSpeakers())
                                    .minSpeakers(uploadRequest.getMinSpeakers())
                                    .maxSpeakers(uploadRequest.getMaxSpeakers())
                                    .build();

                            // Kick off the whisper job asynchronously
                            processJobAsync(savedJob, request, jobId).subscribe();

                            return Mono.just(ResponseEntity.<WhisperResponse>ok(
                                    WhisperResponse.builder()
                                            .jobId(jobId.toString()).build()
                            ));
                        });
                });
            });
        });
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<WhisperResponse> getJob(@PathVariable String jobId) {
        try {
            UUID uuid = UUID.fromString(jobId);
            WhisperJob job = whisperJobRepository.findById(uuid).block();
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
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
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<WhisperCollectionResponse> listJobs() {
        try {
            List<String> jobIds = whisperJobRepository.findAll()
                .map(job -> job.getId().toString())
                .collectList()
                .block();
            WhisperCollectionResponse response = new WhisperCollectionResponse(jobIds);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var inputStream = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        });
    }


    private Mono<Void> processJobAsync(WhisperJob job, WhisperRequest request, UUID jobId) {
        return Mono.fromCallable(() -> {
                    kickOffWhisperJob(request, jobId);
                    return (Void) null;
                })
                .then(Mono.fromCallable(() -> {
                    String transcript = Files.readString(Paths.get(transcriptOutputBasePath).resolve(jobId.toString()).resolve("transcript.txt"));
                    job.setStatus("completed");
                    job.setTranscriptText(transcript);
                    return job;
                }))
                .flatMap(updatedJob -> whisperJobRepository.save(updatedJob))
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

       /**
       * TODO Needs to save the output in a db with the job id
       *   a lot of what initially went into the other service actually needs to go here.
       *   The other service will just be handing requests off between the different models
       *   but we need these different steps to be hosted by different services so that
       *   they can be scaled independently
        */
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


}