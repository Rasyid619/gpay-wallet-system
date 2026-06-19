package com.gpay.wallet_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared PostgreSQL Testcontainers instance reused across every wallet-service test in the JVM.
 *
 * <p>A single container is started once on first access and reused by all Spring contexts,
 * so {@code @SpringBootTest} tests run against a disposable database with Flyway-migrated
 * schema instead of depending on a manually started local Postgres.
 */
public final class WalletPostgresContainer {

	private static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

	static {
		INSTANCE.start();
	}

	private WalletPostgresContainer() {
	}

	/**
	 * Wires the shared container's datasource and disables Kafka listener startup so the
	 * context boots without a running broker.
	 *
	 * @param registry dynamic property registry for the test context
	 */
	public static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", INSTANCE::getJdbcUrl);
		registry.add("spring.datasource.username", INSTANCE::getUsername);
		registry.add("spring.datasource.password", INSTANCE::getPassword);
		registry.add("spring.kafka.listener.auto-startup", () -> "false");
	}
}
