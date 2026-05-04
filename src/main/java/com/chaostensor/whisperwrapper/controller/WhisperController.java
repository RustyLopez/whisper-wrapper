package com.chaostensor.whisperwrapper.controller;


import com.chaostensor.whisperwrapper.dto.WhisperRequest;
import com.chaostensor.whisperwrapper.dto.WhisperResponse;
import com.chaostensor.whisperwrapper.dto.WhisperUploadRequest;
import com.chaostensor.whisperwrapper.dto.CompletedStatus;
import com.chaostensor.whisperwrapper.dto.WhisperCollectionResponse;
import com.chaostensor.whisperwrapper.dto.PendingStatus;
import com.chaostensor.whisperwrapper.entity.WhisperJob;
import com.chaostensor.whisperwrapper.repository.WhisperJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
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
@Slf4j
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

    // WhisperX configuration properties
    /**
     * Default Whisper model to use if not specified by user.
     */
    @Value("${app.whisperx.default-model}")
    String defaultModel;

    /**
     * Default batch size for inference.
     */
    @Value("${app.whisperx.batch-size}")
    Integer batchSize;

    /**
     * Default compute type for computation.
     */
    @Value("${app.whisperx.compute-type}")
    String computeType;

    /**
     * Default alignment model (auto-selected if empty).
     */
    @Value("${app.whisperx.default-align-model:}")
    String defaultAlignModel;

    /**
     * Default VAD method.
     */
    @Value("${app.whisperx.default-vad-method}")
    String defaultVadMethod;

    /**
     * Default output format.
     */
    @Value("${app.whisperx.default-output-format}")
    String outputFormat;

    /**
     * Device to use for inference.
     */
    @Value("${app.whisperx.device}")
    String device;

    /**
     * Device index for FasterWhisper inference.
     */
    @Value("${app.whisperx.device-index}")
    Integer deviceIndex;

    /**
     * Whether to print progress during transcription.
     */
    @Value("${app.whisperx.print-progress}")
    Boolean printProgress;

    /**
     * HuggingFace token for accessing gated models (diarization).
     */
    //@Value("${app.whisperx.hf-token:}")
    // TODO Not sure we want to allow the user to pass this in.
            // IF we do we need to ensure it doesn't get stored, or if it's stored it's like GDPR deletable and
            // Encrypted..etc  um for now.
    //String hfToken;

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
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<WhisperResponse>> create(@RequestBody Mono<WhisperRequest> request) {


        return request.flatMap(whisperRequest -> {
            final UUID jobId = UUID.randomUUID();

            Path filePath = Paths.get(mediaBasePath).resolve(whisperRequest.getFileName());

            return computeHashAndCheckExists(filePath)
                    .flatMap(hashAndExists -> {
                        if (hashAndExists.exists()) {
                            return Mono.error(new DuplicateRequestException());
                        }
                        return createAndStartJob(jobId, hashAndExists.hash(), whisperRequest.getFileName(), whisperRequest);
                    });
        })
                .doOnError(e -> {
                    log.error("create error", e);
                });


    }

    /**
     * Alternative version of createJob that accepts a video file upload.
     * Saves the uploaded file to mediaBasePath and kicks off the whisper job.
     * Uses hash of file content + UUID as filename, checks for duplicates.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<WhisperResponse>> createFromUpload(@RequestPart("file") MultipartFile file,
                                                                   @RequestPart(value = "task", required = false) String task,
                                                                   @RequestPart(value = "language", required = false) String language,
                                                                   @RequestPart(value = "timestamp", required = false) String timestamp,
                                                                   @RequestPart(value = "numSpeakers", required = false) Integer numSpeakers,
                                                                   @RequestPart(value = "minSpeakers", required = false) Integer minSpeakers,
                                                                   @RequestPart(value = "maxSpeakers", required = false) Integer maxSpeakers,
                                                                   @RequestPart(value = "model", required = false) String model,
                                                                   @RequestPart(value = "outputFormat", required = false) String outputFormat,
                                                                   @RequestPart(value = "diarize", required = false) Boolean diarize,
                                                                   @RequestPart(value = "alignModel", required = false) String alignModel,
                                                                   @RequestPart(value = "vadMethod", required = false) String vadMethod,
                                                                   @RequestPart(value = "vadOnset", required = false) Float vadOnset,
                                                                   @RequestPart(value = "vadOffset", required = false) Float vadOffset,
                                                                   @RequestPart(value = "chunkSize", required = false) Integer chunkSize,
                                                                   @RequestPart(value = "diarizeModel", required = false) String diarizeModel,
                                                                   @RequestPart(value = "temperature", required = false) Float temperature,
                                                                   @RequestPart(value = "beamSize", required = false) Integer beamSize,
                                                                   @RequestPart(value = "highlightWords", required = false) Boolean highlightWords,
                                                                   @RequestPart(value = "hotwords", required = false) String hotwords) {

        final UUID jobId = UUID.randomUUID();

        WhisperUploadRequest uploadRequest = WhisperUploadRequest.builder()
                .file(file)
                .task(task)
                .language(language)
                .timestamp(timestamp)
                .numSpeakers(numSpeakers)
                .minSpeakers(minSpeakers)
                .maxSpeakers(maxSpeakers)
                .model(model)
                .outputFormat(outputFormat)
                .diarize(diarize)
                .alignModel(alignModel)
                .vadMethod(vadMethod)
                .vadOnset(vadOnset)
                .vadOffset(vadOffset)
                .chunkSize(chunkSize)
                .diarizeModel(diarizeModel)
                .temperature(temperature)
                .beamSize(beamSize)
                .highlightWords(highlightWords)
                .hotwords(hotwords)
                .build();

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
                            .model(uploadRequest.getModel())
                            .diarize(uploadRequest.getDiarize())
                            .alignModel(uploadRequest.getAlignModel())
                            .vadMethod(uploadRequest.getVadMethod())
                            .vadOnset(uploadRequest.getVadOnset())
                            .vadOffset(uploadRequest.getVadOffset())
                            .chunkSize(uploadRequest.getChunkSize())
                            .diarizeModel(uploadRequest.getDiarizeModel())
                            .temperature(uploadRequest.getTemperature())
                            .beamSize(uploadRequest.getBeamSize())
                            .highlightWords(uploadRequest.getHighlightWords())
                            .hotwords(uploadRequest.getHotwords())
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
                        WhisperResponse response = WhisperResponse.builder().jobId(jobId).status(job.getStatus()).build();
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
        WhisperJob job = new WhisperJob(jobId, hash, new PendingStatus("pending"), null, filename);
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
                .flatMap(hash -> whisperJobRepository.findByHash(hash)
                        .flatMap(job -> Mono.just(new HashAndFilename(hash, job.getVideoPath())))
                        .switchIfEmpty(Mono.defer(() -> {
                            String uniqueFilename = jobId.toString();
                            return saveFile(file, uniqueFilename)
                                    .then(Mono.just(new HashAndFilename(hash, uniqueFilename)));
                        })));
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
        return kickOffWhisperJob(request, jobId)
                .then(Mono.fromCallable(() -> {
                    // WhisperX creates multiple output files in a jobId-specific directory
                    // We want to read the .srt file which has the original filename with .srt extension
                    Path outputDir = Paths.get(transcriptOutputBasePath).resolve(jobId.toString());
                    String originalFilename = request.getFileName();
                    // Remove extension from original filename and add .srt
                    String srtFilename = originalFilename.contains(".")
                        ? originalFilename.substring(0, originalFilename.lastIndexOf('.')) + ".srt"
                        : originalFilename + ".srt";
                    Path srtFilePath = outputDir.resolve(srtFilename);

                    // Read the .srt file content
                    String transcript = Files.readString(srtFilePath);
                    job.setStatus(new CompletedStatus("completed", transcript));
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
                    job.setStatus(new PendingStatus("failed"));
                    whisperJobRepository.save(job).subscribe(); // fire and forget
                });
    }

    private Mono<Void> kickOffWhisperJob(final WhisperRequest request, final UUID jobId) {
        return Mono.fromCallable(() -> {
            final Process process;
            try {
            List<String> command = new ArrayList<>();
            command.add("whisperx");

            // Input audio file path
            command.add(Paths.get(mediaBasePath).resolve(request.getFileName()).normalize().toString());

            // Output directory - create a video-id specific subdirectory
            Path outputDir = Paths.get(transcriptOutputBasePath).resolve(jobId.toString());
            command.add("--output_dir");
            command.add(outputDir.toString());

            // Model selection - use user specified or default
            String model = (request.getModel() != null && !request.getModel().isEmpty()) ? request.getModel() : defaultModel;
            command.add("--model");
            command.add(model);

            // Device configuration
            command.add("--device");
            command.add(device);
            if ("cuda".equals(device)) {
                command.add("--device_index");
                command.add(deviceIndex.toString());
            }

            // Batch size : This depends heavily on deployment env resources and is not something the user should change from the api layer.
            Integer batchSize = this.batchSize;
            command.add("--batch_size");
            command.add(batchSize.toString());

            // Compute type - use user specified or default
            String computeType = (request.getComputeType() != null && !request.getComputeType().isEmpty()) ? request.getComputeType() : this.computeType;
            command.add("--compute_type");
            command.add(computeType);

            // Task - transcribe or translate
            String task = (request.getTask() != null && !request.getTask().isEmpty()) ? request.getTask() : "transcribe";
            command.add("--task");
            command.add(task);

            // Language - if specified
            if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
                command.add("--language");
                command.add(request.getLanguage());
            }

            // Output format - use user specified or default (we want .srt)
            /*
             * TODO we are hard coding an assumption that this is srt ( or all ).
             *
             * We need to make it configurable. IF we do then we could let the end user specify this.
             *
             * For now no...
             */
            String outputFormat = this.outputFormat;// (request.getOutputFormat() != null && !request.getOutputFormat().isEmpty()) ? request.getOutputFormat() : this.outputFormat;
            command.add("--output_format");
            command.add(outputFormat);

            // Alignment model - use user specified or default
            String alignModel = (request.getAlignModel() != null && !request.getAlignModel().isEmpty()) ? request.getAlignModel() : defaultAlignModel;
            if (alignModel != null && !alignModel.isEmpty()) {
                command.add("--align_model");
                command.add(alignModel);
            }

            // VAD method - use user specified or default
            String vadMethod = (request.getVadMethod() != null && !request.getVadMethod().isEmpty()) ? request.getVadMethod() : defaultVadMethod;
            command.add("--vad_method");
            command.add(vadMethod);

            // VAD parameters - use user specified or defaults
            if (request.getVadOnset() != null) {
                command.add("--vad_onset");
                command.add(request.getVadOnset().toString());
            }
            if (request.getVadOffset() != null) {
                command.add("--vad_offset");
                command.add(request.getVadOffset().toString());
            }
            if (request.getChunkSize() != null) {
                command.add("--chunk_size");
                command.add(request.getChunkSize().toString());
            }

            // Diarization options
            Boolean diarize = request.getDiarize() != null ? request.getDiarize() : false;
            if (diarize) {
                command.add("--diarize");
                // Speaker count options
                if (request.getNumSpeakers() != null) {
                    command.add("--min_speakers");
                    command.add(request.getNumSpeakers().toString());
                    command.add("--max_speakers");
                    command.add(request.getNumSpeakers().toString());
                } else {
                    if (request.getMinSpeakers() != null) {
                        command.add("--min_speakers");
                        command.add(request.getMinSpeakers().toString());
                    }
                    if (request.getMaxSpeakers() != null) {
                        command.add("--max_speakers");
                        command.add(request.getMaxSpeakers().toString());
                    }
                }
                // Diarization model - use user specified or default
                String diarizeModel = (request.getDiarizeModel() != null && !request.getDiarizeModel().isEmpty()) ? request.getDiarizeModel() : "pyannote/speaker-diarization-community-1";
                command.add("--diarize_model");
                command.add(diarizeModel);
            }

            // Sampling parameters
            if (request.getTemperature() != null) {
                command.add("--temperature");
                command.add(request.getTemperature().toString());
            }
            if (request.getBeamSize() != null) {
                command.add("--beam_size");
                command.add(request.getBeamSize().toString());
            }

            // Highlight words in output
            if (request.getHighlightWords() != null && request.getHighlightWords()) {
                command.add("--highlight_words");
                command.add("True");
            }

            // Hotwords for better recognition
            if (request.getHotwords() != null && !request.getHotwords().isEmpty()) {
                command.add("--hotwords");
                command.add(request.getHotwords());
            }

            // HuggingFace token for gated models
            // TODO Not sure we want to allow the user to pass this in.
            // IF we do we need to ensure it doesn't get stored, or if it's stored it's like GDPR deletable and
            // Encrypted..etc  um for now.
            // String hfTokenToUse = (hfToken != null && !hfToken.isEmpty()) ? hfToken : null;
            //if (hfTokenToUse != null) {
            //    command.add("--hf_token");
            //   command.add(hfTokenToUse);
            //}

            // Progress printing
            if (printProgress != null && printProgress) {
                command.add("--print_progress");
                command.add("True");
            }

            // Legacy timestamp parameter (for backward compatibility if still used)
            if (request.getTimestamp() != null && !request.getTimestamp().isEmpty()) {
                // whisperX uses different timestamp handling, but we'll keep this for compatibility
                // Note: whisperX has different segment resolution options
                command.add("--segment_resolution");
                command.add(request.getTimestamp()); // "sentence" or "chunk"
            }

            log.info("command to run: command" + command);
            process = Runtime.getRuntime().exec(command.toArray(new String[0]));
        } catch (IOException e) {
            throw new RuntimeException("failed to initialize the whisperx process", e);
        }

        final BufferedReader solveOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        final BufferedReader solveErrors = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        solveOutput.lines().forEach(System.out::println);

        try {
            /**
             * WhisperX can take significant time depending on model, batch size, and media length.
             * Setting to 4 hours for now, which may need adjustment based on production usage.
             */
            process.onExit().get(4, TimeUnit.HOURS);
        } catch (TimeoutException te) {
            try {
                process.destroy();
            } catch (RuntimeException processTerminateFailure) {
                te.addSuppressed(processTerminateFailure);
            }
            throw new RuntimeException("WhisperX process failed to complete in time: " + solveErrors.lines().collect(Collectors.joining()), te);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("WhisperX process error: " + solveErrors.lines().collect(Collectors.joining()), e);
        }
            if (process.exitValue() != 0) {
                throw new RuntimeException("WhisperX process failed: " + solveErrors.lines().collect(Collectors.joining()));
            }
            return null;
        });
    }

    // Record classes for flattening reactive chains
    private record HashAndExists(String hash, boolean exists) {
    }

    private record HashAndFilename(String hash, String filename) {
    }

}