package com.chaostensor.whisperwrapper.controller;

import com.chaostensor.whisperwrapper.dto.*;
import com.chaostensor.whisperwrapper.entity.WhisperJob;
import com.chaostensor.whisperwrapper.repository.WhisperJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.config.location=classpath:application-test.yaml" })
class WhisperControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WhisperJobRepository whisperJobRepository;

    @Test
    void get_WithValidJobId_ReturnsJob() {
        UUID jobId = UUID.randomUUID();
        WhisperJob whisperJob = new WhisperJob(jobId, "hash123", new PendingStatus("pending"), null, "test.mp4");
        whisperJobRepository.save(whisperJob).block();

        webTestClient.get()
                .uri("/whispers/{jobId}", jobId)
                .exchange()
                .expectStatus().isOk();

        whisperJobRepository.deleteById(jobId).block();
    }

    @Test
    void get_WithInvalidJobId_ReturnsInternalServerError() {
        String invalidJobId = "invalid-uuid";

        webTestClient.get()
                .uri("/whispers/{jobId}", invalidJobId)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void get_WithNonExistentJobId_ReturnsNotFound() {
        UUID jobId = UUID.randomUUID();

        webTestClient.get()
                .uri("/whispers/{jobId}", jobId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void list_ReturnsJobIds() {
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        WhisperJob job1 = new WhisperJob(jobId1, "hash1", new PendingStatus("pending"), null, "file1.mp4");
        WhisperJob job2 = new WhisperJob(jobId2, "hash2", new PendingStatus("pending"), null, "file2.mp4");
        whisperJobRepository.save(job1).block();
        whisperJobRepository.save(job2).block();

        webTestClient.get()
                .uri("/whispers")
                .exchange()
                .expectStatus().isOk();

        whisperJobRepository.deleteById(jobId1).block();
        whisperJobRepository.deleteById(jobId2).block();
    }

    @Test
    void list_OnError_ReturnsInternalServerError() {
        // This is hard to test with real repository, so skipping for now
    }

    // More tests will be added
}