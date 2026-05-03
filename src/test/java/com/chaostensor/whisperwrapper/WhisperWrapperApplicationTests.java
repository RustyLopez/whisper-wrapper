package com.chaostensor.whisperwrapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@Testcontainers
@SpringBootTest
@TestPropertySource(properties = { "spring.config.location=classpath:application-test.yaml" })
public class WhisperWrapperApplicationTests {

	@Container
	static PostgreSQLContainer<?> postgresWithVector = new PostgreSQLContainer<>("pgvector/pgvector:pg18")
			.withDatabaseName("testdb")
			.withUsername("test")
			.withPassword("test");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.r2dbc.url", postgresWithVector::getR2dbcUrl);
		registry.add("spring.r2dbc.username", postgresWithVector::getUsername);
		registry.add("spring.r2dbc.password", postgresWithVector::getPassword);

		registry.add("spring.datasource.url", postgresWithVector::getJdbcUrl);
		registry.add("spring.datasource.username", postgresWithVector::getUsername);
		registry.add("spring.datasource.password", postgresWithVector::getPassword);
		registry.add("spring.datasource.driver-class-name", "rg.postgresql.Driver");

	}

	@Autowired
	private DatabaseClient databaseClient;


	@Test
	void contextLoads() {
	}

}
