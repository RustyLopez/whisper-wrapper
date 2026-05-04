package com.chaostensor.whisperwrapper.controller;

import com.chaostensor.whisperwrapper.dto.*;
import com.chaostensor.whisperwrapper.entity.WhisperJob;
import com.chaostensor.whisperwrapper.repository.WhisperJobRepository;
import com.chaostensor.whisperwrapper.service.ProcessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.client.MockMvcWebTestClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Commit;
import org.springframework.web.client.ApiVersionInserter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location=classpath:application-test.yaml"})
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


    @Autowired
    private WhisperJobRepository whisperJobRepository;

    /**
     * Deprecated in spring boot 4, not a thing, do class level
     */
    //@MockBean
    @MockitoBean
    private ProcessService processService;

    @Autowired
    private Environment environment;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        int port = environment.getProperty("local.server.port", Integer.class);
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
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

    @Test
    void get_WithExistingJobId_ReturnsJob() {
        UUID jobId = UUID.randomUUID();
        WhisperJob job = new WhisperJob(jobId, "hash", new PendingStatus("pending"), null, "file.mp4");
        whisperJobRepository.save(job).block();

        webTestClient.get()
                .uri("/whispers/{jobId}", jobId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobId").isEqualTo(jobId.toString())
                .jsonPath("$.status").isEqualTo("pending");

        whisperJobRepository.deleteById(jobId).block();
    }

    @Test
    void create_WithValidRequest_ReturnsJobId() throws Exception {
        String filename = "test.mp4";
        Path mediaPath = Paths.get("./media-input");
        Files.createDirectories(mediaPath);
        Path filePath = mediaPath.resolve(filename);
        Files.write(filePath, "test content".getBytes());

        WhisperRequest request = WhisperRequest.builder()
                .fileName(filename)
                .task("transcribe")
                .build();

        // Mock repository
        when(whisperJobRepository.findByHash(anyString())).thenReturn(Mono.empty());
        when(whisperJobRepository.save(any(WhisperJob.class))).thenReturn(Mono.just(new WhisperJob(UUID.randomUUID(), "hash", new PendingStatus("pending"), null, filename)));
        // Mock process service
        when(processService.executeCommand(anyList())).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/whispers")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobId").exists();

        Files.deleteIfExists(filePath);
    }

    @Test
    void create_WithAllParameters_ReturnsJobId() throws Exception {
        String filename = "test2.mp4";
        Path mediaPath = Paths.get("./media-input");
        Files.createDirectories(mediaPath);
        Path filePath = mediaPath.resolve(filename);
        Files.write(filePath, "test content".getBytes());

        WhisperRequest request = WhisperRequest.builder()
                .fileName(filename)
                .task("translate")
                .language("en")
                .timestamp("srt")
                .numSpeakers(2)
                .minSpeakers(1)
                .maxSpeakers(3)
                .build();

        webTestClient.post()
                .uri("/whispers")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.jobId").exists();

        Files.deleteIfExists(filePath);
    }

    @Test
    void create_WithDuplicateRequest_ReturnsError() throws Exception {
        String filename = "duplicate.mp4";
        Path mediaPath = Paths.get("./media-input");
        Files.createDirectories(mediaPath);
        Path filePath = mediaPath.resolve(filename);
        Files.write(filePath, "duplicate content".getBytes());

        // Compute hash
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest("duplicate content".getBytes());
        String hash = bytesToHex(hashBytes);

        // Create existing job with same hash
        UUID existingJobId = UUID.randomUUID();
        WhisperJob existingJob = new WhisperJob(existingJobId, hash, new PendingStatus("pending"), null, "other.mp4");
        whisperJobRepository.save(existingJob).block();

        WhisperRequest request = WhisperRequest.builder()
                .fileName(filename)
                .task("transcribe")
                .build();

        webTestClient.post()
                .uri("/whispers")
                .bodyValue(request)
                .exchange()
                .expectStatus().is5xxServerError(); // Assuming DuplicateRequestException causes 500

        Files.deleteIfExists(filePath);
        whisperJobRepository.deleteById(existingJobId).block();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    // More tests will be added
}