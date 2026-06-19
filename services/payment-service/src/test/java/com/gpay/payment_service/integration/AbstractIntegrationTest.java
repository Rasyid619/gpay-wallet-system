package com.gpay.payment_service.integration;

import com.gpay.payment_service.support.PaymentTestContainers;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/*
 * Shared PostgreSQL + Redis Testcontainers base for Payment Service integration tests that
 * exercise real HTTP, persistence, locking, Redis-backed rate limiting, and the Flyway-migrated
 * schema.
 *
 * The containers are provided by PaymentTestContainers and reused across all subclasses in the
 * same JVM. Flyway runs the payment_db migrations against the container on context start. Shared
 * cleanup removes Postgres data in foreign-key-safe order before each test; the Redis-backed
 * rate-limit test flushes Redis itself so windows do not leak. Kafka listener auto-startup is
 * disabled so the context boots without a running broker.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

	protected static final String JWT_SECRET = PaymentTestContainers.JWT_SECRET;
	protected static final String GATEWAY_WEBHOOK_SECRET = PaymentTestContainers.GATEWAY_WEBHOOK_SECRET;
	protected static final int RATE_LIMIT_MAX_REQUESTS_PER_MINUTE =
			PaymentTestContainers.RATE_LIMIT_MAX_REQUESTS_PER_MINUTE;

	private static final String HMAC_SHA256 = "HmacSHA256";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		PaymentTestContainers.registerProperties(registry);
	}

	@BeforeEach
	void cleanState() {
		jdbcTemplate.update("DELETE FROM outbox_events");
		jdbcTemplate.update("DELETE FROM activity_logs");
		jdbcTemplate.update("DELETE FROM idempotency_keys");
		jdbcTemplate.update("DELETE FROM topup_transactions");
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
	 * Computes the gateway webhook HMAC signature the service expects for a raw body.
	 *
	 * @param timestamp      webhook timestamp header value
	 * @param rawRequestBody exact raw JSON body bytes the service will read
	 * @return hex-encoded HmacSHA256 of {@code timestamp.rawRequestBody}
	 */
	protected String signWebhook(String timestamp, String rawRequestBody) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(GATEWAY_WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			byte[] signature = mac.doFinal((timestamp + "." + rawRequestBody).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(signature);
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to sign test webhook payload", ex);
		}
	}
}
