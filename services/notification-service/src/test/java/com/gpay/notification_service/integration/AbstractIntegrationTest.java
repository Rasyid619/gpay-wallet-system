package com.gpay.notification_service.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/*
 * Shared PostgreSQL Testcontainers base for Notification Service integration tests that
 * exercise real persistence and Flyway-migrated schema behavior.
 *
 * A single container is started once and reused across all subclasses in the same JVM.
 * Flyway runs the notification_db migrations against the container on context start.
 * Kafka listener auto-startup is disabled so the context boots without a running broker.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.kafka.listener.auto-startup", () -> "false");
	}

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM notification_attempts");
	}
}
