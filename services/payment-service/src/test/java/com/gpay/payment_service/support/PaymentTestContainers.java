package com.gpay.payment_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared PostgreSQL and Redis Testcontainers reused across every payment-service
 * Spring context in the JVM.
 *
 * <p>Both containers start once on first access and are reused by all
 * {@code @SpringBootTest} contexts, so tests run against a disposable
 * Flyway-migrated database and a real Redis instead of manually started local
 * infrastructure. {@link #registerProperties} wires the datasource, Redis
 * connection, webhook/JWT secrets, a small rate-limit ceiling, and long outbox
 * worker delays, and disables Kafka listener startup so the context boots
 * without a running broker.
 */
public final class PaymentTestContainers {

	/** Webhook HMAC secret shared between the signature helper and the running context. */
	public static final String GATEWAY_WEBHOOK_SECRET = "integration-test-gateway-webhook-secret";

	/** JWT signing secret used to mint test access tokens. */
	public static final String JWT_SECRET = "integration-test-secret-minimum-32-characters-long";

	/** Per-user top-up ceiling kept small so the rate-limit deny path is fast. */
	public static final int RATE_LIMIT_MAX_REQUESTS_PER_MINUTE = 3;

	private static final int REDIS_PORT = 6379;

	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	private static final GenericContainer<?> REDIS =
			new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

	static {
		POSTGRES.start();
		REDIS.start();
	}

	private PaymentTestContainers() {
	}

	/**
	 * Wires datasource, Redis, secrets, rate-limit, Kafka, and outbox worker
	 * properties for a payment-service test context.
	 *
	 * @param registry dynamic property registry for the test context
	 */
	public static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);

		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));

		registry.add("jwt.secret", () -> JWT_SECRET);
		registry.add("payment.webhook.gateway-secret", () -> GATEWAY_WEBHOOK_SECRET);
		registry.add(
				"payment.rate-limit.max-requests-per-minute",
				() -> String.valueOf(RATE_LIMIT_MAX_REQUESTS_PER_MINUTE));

		registry.add("payment.gateway.top-up-url", () -> "http://localhost:8084/mock-gateway/top-up");
		registry.add("payment.gateway.timeout-ms", () -> "5000");

		registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
		registry.add("spring.kafka.listener.auto-startup", () -> "false");
		registry.add("payment.kafka.wallet-credit-commands-topic", () -> "wallet.credit.commands");

		registry.add("payment.outbox.retry-delay-ms", () -> "60000");
		registry.add("payment.outbox.max-attempts", () -> "10");
		registry.add("payment.outbox.max-age-ms", () -> "86400000");
		registry.add("payment.outbox.processing-timeout-ms", () -> "300000");
		registry.add("payment.outbox.batch-size", () -> "10");
		registry.add("payment.outbox.worker-fixed-delay-ms", () -> "3600000");
		registry.add("payment.outbox.worker-initial-delay-ms", () -> "3600000");
	}
}
