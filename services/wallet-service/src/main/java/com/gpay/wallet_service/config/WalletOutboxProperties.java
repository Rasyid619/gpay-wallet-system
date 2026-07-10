package com.gpay.wallet_service.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/* Retry and worker tuning for the wallet transfer-event outbox. */
@Validated
@ConfigurationProperties(prefix = "wallet.outbox")
public record WalletOutboxProperties(
		@NotNull @Positive Long retryDelayMs,
		@NotNull @Positive Integer maxAttempts,
		@NotNull @Positive Long maxAgeMs,
		@NotNull @Positive Long processingTimeoutMs,
		@NotNull @Positive Integer batchSize,
		@NotNull @Positive Long workerFixedDelayMs,
		@NotNull @Positive Long workerInitialDelayMs) {
}
