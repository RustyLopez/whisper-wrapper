package com.chaostensor.video_notes_to_wiki.controller;

import com.chaostensor.video_notes_to_wiki.service.VideoProcessingService;
import com.chaostensor.video_notes_to_wiki.entity.Job;
import com.chaostensor.video_notes_to_wiki.entity.JobStatus;
import com.chaostensor.video_notes_to_wiki.repository.JobRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/whisper")
public class WhisperController {


    public WhisperController() {

    }

    @PostMapping
    public Mono<ResponseEntity<JobResponse>> createJob(@RequestBody WhisperRequest request) {

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("whisperx", "path/to/video.mp4"/* TODO From args, and we need to work out how the clien twill know wher ethe files are. There will be a shared volume mount so. yeah */)

                // todo remember how to do the execution and add all the error handling. Can use the latent stacker as a ref
        processBuilder.build().execute()
    }

    @GetMapping("/{id}/transcripts")
    public Mono<ResponseEntity<List<String>>> getTranscripts(@PathVariable UUID id) {
        return jobRepository.findById(id)
            .<ResponseEntity<List<String>>>flatMap(job -> {
                if (job.getStatus() != JobStatus.COMPLETED) {
                    return Mono.just(ResponseEntity.badRequest().build());
                }
                try {
                    Map<String, String> transcripts = objectMapper.readValue(job.getTranscriptsJson(), Map.class);
                    return Mono.just(ResponseEntity.<List<String>>ok(transcripts.keySet().stream().toList()));
                } catch (Exception e) {
                    return Mono.error(e);
                }
            })
            .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }

    @GetMapping("/{id}/transcripts/{subId}")
    public Mono<ResponseEntity<String>> getTranscript(@PathVariable UUID id, @PathVariable String subId) {
        return jobRepository.findById(id)
            .<ResponseEntity<String>>flatMap(job -> {
                if (job.getStatus() != JobStatus.COMPLETED) {
                    return Mono.just(ResponseEntity.badRequest().build());
                }
                try {
                    Map<String, String> transcripts = objectMapper.readValue(job.getTranscriptsJson(), Map.class);
                    String transcript = transcripts.get(subId);
                    if (transcript != null) {
                        return Mono.just(ResponseEntity.ok(transcript));
                    } else {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                } catch (Exception e) {
                    return Mono.error(e);
                }
            })
                .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
    }
}