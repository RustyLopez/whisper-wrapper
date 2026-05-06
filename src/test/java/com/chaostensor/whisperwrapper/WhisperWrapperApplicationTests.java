package com.chaostensor.whisperwrapper;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles(profiles = "test")
@TestPropertySource
public class WhisperWrapperApplicationTests {

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
	private DatabaseClient databaseClient;


	@Test
	void contextLoads() {
	}

}
