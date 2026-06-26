package com.gpay.wallet_service.integration;

import com.gpay.wallet_service.support.WalletPostgresContainer;
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

/*
 * Shared PostgreSQL Testcontainers base for Wallet Service integration tests that exercise
 * real HTTP, persistence, locking, and Flyway-migrated schema behavior.
 *
 * The container is provided by WalletPostgresContainer and reused across all subclasses in
 * the same JVM. Flyway runs the wallet_db migrations against the container on context start.
 * Shared cleanup removes data in foreign-key-safe order before each test. Kafka listener
 * auto-startup is disabled so the context boots without a running broker.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

	protected static final String JWT_SECRET = "integration-test-secret-minimum-32-characters-long";
	protected static final String INTERNAL_TOKEN = "integration-test-internal-token";
	protected static final long MAX_DAILY_TRANSFER_AMOUNT = 100_000L;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		WalletPostgresContainer.registerProperties(registry);
		registry.add("jwt.secret", () -> JWT_SECRET);
		registry.add("wallet.internal-token", () -> INTERNAL_TOKEN);
		registry.add("wallet.transfer.max-daily-transfer-amount", () -> String.valueOf(MAX_DAILY_TRANSFER_AMOUNT));
		registry.add("wallet.reconciliation.enabled", () -> "false");
	}

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM wallet_reconciliation_mismatches");
		jdbcTemplate.update("DELETE FROM wallet_reconciliation_runs");
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

	/**
	 * Mints a valid HMAC-SHA256 access token carrying a role claim for the supplied user.
	 *
	 * @param userId subject the token authenticates
	 * @param role   role claim granted to the token, for example {@code ADMIN} or {@code USER}
	 * @return signed compact JWT string
	 */
	protected String createToken(UUID userId, String role) {
		Instant now = Instant.now();
		SecretKey signingKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
		return Jwts.builder()
				.subject(userId.toString())
				.claim("email", "user@example.com")
				.claim("role", role)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(900)))
				.signWith(signingKey)
				.compact();
	}
}
