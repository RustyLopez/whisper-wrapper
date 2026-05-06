package com.chaostensor.whisperwrapper.controller;


import com.chaostensor.whisperwrapper.dto.WhisperRequest;
import com.chaostensor.whisperwrapper.dto.WhisperResponse;
import com.chaostensor.whisperwrapper.dto.WhisperUploadRequest;
import com.chaostensor.whisperwrapper.dto.CompletedStatus;
import com.chaostensor.whisperwrapper.dto.WhisperCollectionResponse;
import com.chaostensor.whisperwrapper.dto.PendingStatus;
import com.chaostensor.whisperwrapper.entity.WhisperJob;
import com.chaostensor.whisperwrapper.repository.WhisperJobRepository;
import com.chaostensor.whisperwrapper.service.ProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import com.google.common.collect.ImmutableList;

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
    private final ProcessService processService;


    public WhisperController(final WhisperJobRepository whisperJobRepository, final ProcessService processService) {
        this.whisperJobRepository = whisperJobRepository;
        this.processService = processService;
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
            final Path filePath = Paths.get(mediaBasePath).resolve(whisperRequest.getFileName());

            return computeHashAndCheckExists(filePath)
                    .flatMap(hashAndExists -> {
                        if (hashAndExists.exists()) {
                            return Mono.error(new DuplicateRequestException());
                        }
                        return createAndStartJob(hashAndExists.hash(), whisperRequest.getFileName(), whisperRequest);
                    });
        })
                .doOnError(e -> {
                    log.error("create error", e);
                })
                .onErrorResume(e -> {
                    log.error("Error in create job", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });


    }

    /**
     * Alternative version of createJob that accepts a video file upload.
     * Saves the uploaded file to mediaBasePath and kicks off the whisper job.
     * Uses hash of file content + UUID as filename, checks for duplicates.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<WhisperResponse>> createFromUpload(@ModelAttribute WhisperUploadRequest uploadRequest) {

        return computeHashAndCheckExists(uploadRequest.getFile())
                .flatMap(hashAndExists -> {
                    if (hashAndExists.exists()) {
                        return Mono.error(new DuplicateRequestException());
                    }
                    return createJob(hashAndExists.hash(), null)
                             .flatMap(savedJob -> {
                                 final String filename = savedJob.getId().toString();
                                 return saveFile(uploadRequest.getFile(), filename)
                                        .then(Mono.fromCallable(() -> {
                                            savedJob.setVideoPath(filename);
                                            return savedJob;
                                        }))
                                        .flatMap(whisperJobRepository::save);
                            });
                })
                .flatMap(savedJob -> {
                    final WhisperRequest request = WhisperRequest.builder()
                            .fileName(savedJob.getVideoPath())
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
                    startJob(savedJob, request);
                    return Mono.just(ResponseEntity.ok(
                            WhisperResponse.builder()
                                    .jobId(savedJob.getId().toString()).build()
                    ));
                })
                .doOnError(e -> log.error("Error in createFromUpload", e))
                .onErrorResume(e -> {
                    log.error("Error in create from upload", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    @GetMapping("/{jobId}")
    public Mono<ResponseEntity<WhisperResponse>> get(@PathVariable String jobId) {
        final UUID uuid = UUID.fromString(jobId);
        return whisperJobRepository.findById(uuid)
                .doOnError(e -> log.error("Error retrieving job {}", jobId, e))
                .flatMap(job -> {
                    final WhisperResponse response = WhisperResponse.builder().jobId(jobId).status(job.getStatus()).build();
                    return Mono.just(ResponseEntity.ok(response));
                })
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(e -> {
                    log.error("Error in get job {}", jobId, e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    @GetMapping("")
    public Mono<ResponseEntity<WhisperCollectionResponse>> list() {
        return whisperJobRepository.findAll()
                .map(job -> job.getId().toString())
                .collectList()
                .map(jobIds -> {
                    final WhisperCollectionResponse response = new WhisperCollectionResponse(jobIds);
                    return ResponseEntity.ok(response);
                })
                .doOnError(e -> log.error("Error retrieving job list", e))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    /**
     * Computes SHA-256 hash of a file using streaming approach.
     */
    private Mono<String> computeFileHash(Path filePath) {
        return Mono.fromCallable(() -> {
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                return DigestUtils.sha256Hex(inputStream);
            }
        });
    }

    private Mono<String> computeFileHash(FilePart filePart) {
        return filePart.content()
                .reduce(DataBuffer::write)
                .map(buffer -> {
                    try (InputStream inputStream = buffer.asInputStream()) {
                        return DigestUtils.sha256Hex(inputStream);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private Mono<Boolean> checkHashExists(final String hash) {
        return whisperJobRepository.findByHash(hash).hasElement();
    }

    private Mono<Void> saveFile(final FilePart filePart, final String filename) {
        final Path mediaPath = Paths.get(mediaBasePath);
        final Path targetPath = mediaPath.resolve(filename);
        return Mono.fromCallable(() -> {
            Files.createDirectories(mediaPath);
            return null;
        }).then(filePart.transferTo(targetPath));
    }

    private Mono<WhisperJob> createJob(final String hash, final String filename) {
        final WhisperJob job = new WhisperJob(null, hash, new PendingStatus(), null, filename);
        return whisperJobRepository.save(job);
    }

    private void startJob(final WhisperJob job, final WhisperRequest request) {
        processJobAsync(job, request).subscribe();
    }

    private Mono<HashAndExists> computeHashAndCheckExists(final Path filePath) {
        return computeFileHash(filePath)
                .flatMap(hash -> checkHashExists(hash)
                        .map(exists -> new HashAndExists(hash, exists)));
    }

    private Mono<HashAndExists> computeHashAndCheckExists(final FilePart filePart) {
        return computeFileHash(filePart)
                .flatMap(hash -> checkHashExists(hash)
                        .map(exists -> new HashAndExists(hash, exists)));
    }

    private Mono<ResponseEntity<WhisperResponse>> createAndStartJob(final String hash, final String filename, final WhisperRequest request) {
        return createJob(hash, filename)
                .doOnSuccess(job -> startJob(job, request))
                .map(job -> ResponseEntity.ok(
                        WhisperResponse.builder()
                                .jobId(job.getId().toString()).build()
                ));
    }


    private Mono<Void> processJobAsync(final WhisperJob job, final WhisperRequest request) {
        return kickOffWhisperJob(request, job.getId())
                .then(Mono.fromCallable(() -> {
                    // WhisperX creates multiple output files in a jobId-specific directory
                    // We want to read the .srt file which has the original filename with .srt extension
                    final Path outputDir = Paths.get(transcriptOutputBasePath).resolve(job.getId().toString());
                    final String originalFilename = request.getFileName();
                    // Remove extension from original filename and add .srt
                    final String srtFilename = originalFilename.contains(".")
                        ? originalFilename.substring(0, originalFilename.lastIndexOf('.')) + ".srt"
                        : originalFilename + ".srt";
                    final Path srtFilePath = outputDir.resolve(srtFilename);

                    // Read the .srt file content
                    final String transcript = Files.readString(srtFilePath);
                    job.setStatus(new CompletedStatus(transcript));
                    job.setTranscriptText(transcript);
                    return job;
                }))
                .flatMap(whisperJobRepository::save)
                .then(Mono.fromCallable(() -> {
                    final Path source = Paths.get(mediaBasePath).resolve(request.getFileName());
                    final Path dest = Paths.get(videoOutputBasePath).resolve(request.getFileName());
                    Files.createDirectories(dest.getParent());
                    Files.move(source, dest);
                    return (Void) null;
                }))
                .doOnError(e -> {
                    log.error("failed to generate transcript", e);
                    job.setStatus(new FailedStatus());
                    whisperJobRepository.save(job).subscribe(); // fire and forget
                });
    }

    private Mono<Void> kickOffWhisperJob(final WhisperRequest request, final UUID jobId) {
        return Mono.fromCallable(() -> {
            final ImmutableList<String> command = buildWhisperCommand(request, jobId);
            log.info("command to run: {}", command);
            return command;
        }).flatMap(processService::executeCommand);
    }

    private ImmutableList<String> buildWhisperCommand(WhisperRequest request, UUID jobId) {
        final ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add("whisperx");

        // Input audio file path
        builder.add(Paths.get(mediaBasePath).resolve(request.getFileName()).normalize().toString());

        // Output directory - create a video-id specific subdirectory
        final Path outputDir = Paths.get(transcriptOutputBasePath).resolve(jobId.toString());
        builder.add("--output_dir");
        builder.add(outputDir.toString());

        // Model selection - use user specified or default
        final String model = (request.getModel() != null && !request.getModel().isEmpty()) ? request.getModel() : defaultModel;
        builder.add("--model");
        builder.add(model);

        // Device configuration
        builder.add("--device");
        builder.add(device);
        if ("cuda".equals(device)) {
            builder.add("--device_index");
            builder.add(deviceIndex.toString());
        }

        // Batch size : This depends heavily on deployment env resources and is not something the user should change from the api layer.
        final Integer batchSize = this.batchSize;
        builder.add("--batch_size");
        builder.add(batchSize.toString());

        // Compute type - use user specified or default
        final String computeType = (request.getComputeType() != null && !request.getComputeType().isEmpty()) ? request.getComputeType() : this.computeType;
        builder.add("--compute_type");
        builder.add(computeType);

        // Task - transcribe or translate
        final String task = (request.getTask() != null && !request.getTask().isEmpty()) ? request.getTask() : "transcribe";
        builder.add("--task");
        builder.add(task);

        // Language - if specified
        if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
            builder.add("--language");
            builder.add(request.getLanguage());
        }

        // Output format - use user specified or default (we want .srt)
        /*
         * TODO we are hard coding an assumption that this is srt ( or all ).
         *
         * We need to make it configurable. IF we do then we could let the end user specify this.
         *
         * For now no...
         */
        final String outputFormat = this.outputFormat;// (request.getOutputFormat() != null && !request.getOutputFormat().isEmpty()) ? request.getOutputFormat() : this.outputFormat;
        builder.add("--output_format");
        builder.add(outputFormat);

        // Alignment model - use user specified or default
        final String alignModel = (request.getAlignModel() != null && !request.getAlignModel().isEmpty()) ? request.getAlignModel() : defaultAlignModel;
        if (alignModel != null && !alignModel.isEmpty()) {
            builder.add("--align_model");
            builder.add(alignModel);
        }

        // VAD method - use user specified or default
        final String vadMethod = (request.getVadMethod() != null && !request.getVadMethod().isEmpty()) ? request.getVadMethod() : defaultVadMethod;
        builder.add("--vad_method");
        builder.add(vadMethod);

        // VAD parameters - use user specified or defaults
        if (request.getVadOnset() != null) {
            builder.add("--vad_onset");
            builder.add(request.getVadOnset().toString());
        }
        if (request.getVadOffset() != null) {
            builder.add("--vad_offset");
            builder.add(request.getVadOffset().toString());
        }
        if (request.getChunkSize() != null) {
            builder.add("--chunk_size");
            builder.add(request.getChunkSize().toString());
        }

        // Diarization options
        final Boolean diarize = request.getDiarize() != null ? request.getDiarize() : false;
        if (diarize) {
            builder.add("--diarize");
            // Speaker count options
            if (request.getNumSpeakers() != null) {
                builder.add("--min_speakers");
                builder.add(request.getNumSpeakers().toString());
                builder.add("--max_speakers");
                builder.add(request.getNumSpeakers().toString());
            } else {
                if (request.getMinSpeakers() != null) {
                    builder.add("--min_speakers");
                    builder.add(request.getMinSpeakers().toString());
                }
                if (request.getMaxSpeakers() != null) {
                    builder.add("--max_speakers");
                    builder.add(request.getMaxSpeakers().toString());
                }
            }
            // Diarization model - use user specified or default
            final String diarizeModel = (request.getDiarizeModel() != null && !request.getDiarizeModel().isEmpty()) ? request.getDiarizeModel() : "pyannote/speaker-diarization-community-1";
            builder.add("--diarize_model");
            builder.add(diarizeModel);
        }

        // Sampling parameters
        if (request.getTemperature() != null) {
            builder.add("--temperature");
            builder.add(request.getTemperature().toString());
        }
        if (request.getBeamSize() != null) {
            builder.add("--beam_size");
            builder.add(request.getBeamSize().toString());
        }

        // Highlight words in output
        if (request.getHighlightWords() != null && request.getHighlightWords()) {
            builder.add("--highlight_words");
            builder.add("True");
        }

        // Hotwords for better recognition
        if (request.getHotwords() != null && !request.getHotwords().isEmpty()) {
            builder.add("--hotwords");
            builder.add(request.getHotwords());
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
            builder.add("--print_progress");
            builder.add("True");
        }

        // Legacy timestamp parameter (for backward compatibility if still used)
        if (request.getTimestamp() != null && !request.getTimestamp().isEmpty()) {
            // whisperX uses different timestamp handling, but we'll keep this for compatibility
            // Note: whisperX has different segment resolution options
            builder.add("--segment_resolution");
            builder.add(request.getTimestamp()); // "sentence" or "chunk"
        }

        return builder.build();
    }

    // Record classes for flattening reactive chains
    private record HashAndExists(String hash, boolean exists) {
    }

}