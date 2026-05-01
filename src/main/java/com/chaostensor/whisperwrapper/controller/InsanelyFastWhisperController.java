package com.chaostensor.whisperwrapper.controller;


import com.chaostensor.whisperwrapper.dto.WhisperRequest;
import com.chaostensor.whisperwrapper.dto.WhisperResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
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
    @Value("${com.chaostensor.whisperwrapper.media-base-path}")
    String mediaBasePath;


    public InsanelyFastWhisperController() {

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


        kickOffWhisperJob(request);

        /**
         * TODO should have more reason for this mono later when going tot he db to register the job..etc
         */
        return Mono.just(
                ResponseEntity.ok(
                        WhisperResponse.builder()
                                .jobId(jobId.toString()).build()
                )
        );

    }


    /**
     * TODO Needs to save the output in a db with the job id
     *   a lot of what initially went into the other service actually needs to go here.
     *   The other service will just be handing requests off between the different models
     *   but we need these different steps to be hosted by different services so that
     *   they can be scaled independently
     */
     private void kickOffWhisperJob(final WhisperRequest request) {
         final Process process;
         try {
             List<String> command = new ArrayList<>();
             command.add("insanely-fast-whisper");
             command.add("--file-name");
             command.add(Paths.get(mediaBasePath).resolve(request.getPathRelativeSharedVolumeMount()).normalize().toString());

             if (request.getDeviceId() != null && !request.getDeviceId().isEmpty()) {
                 command.add("--device-id");
                 command.add(request.getDeviceId());
             }
             if (request.getTranscriptPath() != null && !request.getTranscriptPath().isEmpty()) {
                 command.add("--transcript-path");
                 command.add(request.getTranscriptPath());
             }
             if (request.getModelName() != null && !request.getModelName().isEmpty()) {
                 command.add("--model-name");
                 command.add(request.getModelName());
             }
             if (request.getTask() != null && !request.getTask().isEmpty()) {
                 command.add("--task");
                 command.add(request.getTask());
             }
             if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
                 command.add("--language");
                 command.add(request.getLanguage());
             }
             if (request.getBatchSize() != null) {
                 command.add("--batch-size");
                 command.add(request.getBatchSize().toString());
             }
             if (request.getFlash() != null && request.getFlash()) {
                 command.add("--flash");
                 command.add("True");
             }
             if (request.getTimestamp() != null && !request.getTimestamp().isEmpty()) {
                 command.add("--timestamp");
                 command.add(request.getTimestamp());
             }
             if (request.getHfToken() != null && !request.getHfToken().isEmpty()) {
                 command.add("--hf-token");
                 command.add(request.getHfToken());
             }
             if (request.getDiarizationModel() != null && !request.getDiarizationModel().isEmpty()) {
                 command.add("--diarization_model");
                 command.add(request.getDiarizationModel());
             }
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