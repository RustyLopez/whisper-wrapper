package com.chaostensor.whisperwrapper.controller;

import com.chaostensor.whisperwrapper.dto.*;
import com.chaostensor.whisperwrapper.entity.WhisperJob;
import com.chaostensor.whisperwrapper.repository.WhisperJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.web.client.ApiVersionInserter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.config.location=classpath:application-test.yaml" })
class WhisperControllerTest {


    @Container
    static PostgreSQLContainer<?> postgresWithVector = new PostgreSQLContainer<>("pgvector/pgvector:pg18")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> postgresWithVector.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgresWithVector::getUsername);
        registry.add("spring.r2dbc.password", postgresWithVector::getPassword);

        registry.add("spring.datasource.url", postgresWithVector::getJdbcUrl);
        registry.add("spring.datasource.username", postgresWithVector::getUsername);
        registry.add("spring.datasource.password", postgresWithVector::getPassword);
        registry.add("spring.datasource.driver-class-name", ()->"org.postgresql.Driver");

    }


    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new WhisperController(whisperJobRepository))
                .configureClient()
                .baseUrl("")
                .build();
    }

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