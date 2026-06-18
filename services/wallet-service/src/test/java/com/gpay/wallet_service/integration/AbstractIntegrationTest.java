package com.gpay.wallet_service.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/*
 * Shared PostgreSQL Testcontainers base for Wallet Service integration tests that exercise
 * real HTTP, persistence, locking, and Flyway-migrated schema behavior.
 *
 * A single container is started once and reused across all subclasses in the same JVM.
 * Flyway runs the wallet_db migrations against the container on context start. Shared
 * cleanup removes data in foreign-key-safe order before each test. Kafka listener
 * auto-startup is disabled so the context boots without a running broker.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

	protected static final String JWT_SECRET = "integration-test-secret-minimum-32-characters-long";
	protected static final String INTERNAL_TOKEN = "integration-test-internal-token";
	protected static final long MAX_DAILY_TRANSFER_AMOUNT = 100_000L;

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
		registry.add("jwt.secret", () -> JWT_SECRET);
		registry.add("wallet.internal-token", () -> INTERNAL_TOKEN);
		registry.add("wallet.transfer.max-daily-transfer-amount", () -> String.valueOf(MAX_DAILY_TRANSFER_AMOUNT));
	}

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM ledger_entries");
		jdbcTemplate.update("DELETE FROM transfers");
		jdbcTemplate.update("DELETE FROM idempotency_keys");
		jdbcTemplate.update("DELETE FROM activity_logs");
		jdbcTemplate.update("DELETE FROM wallets");
	}

	/**
	 * Mints a valid HMAC-SHA256 access token for the supplied user using the test secret.
	 *
	 * @param userId subject the token authenticates
	 * @return signed compact JWT string
	 */
	protected String createToken(UUID userId) {
		Instant now = Instant.now();
		SecretKey signingKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
		return Jwts.builder()
				.subject(userId.toString())
				.claim("email", "user@example.com")
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(900)))
				.signWith(signingKey)
				.compact();
	}
}
