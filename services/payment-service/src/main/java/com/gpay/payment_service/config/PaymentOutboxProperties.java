package com.gpay.payment_service.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payment.outbox")
public record PaymentOutboxProperties(
		@NotNull @Positive Long retryDelayMs,
		@NotNull @Positive Integer maxAttempts,
		@NotNull @Positive Long maxAgeMs,
		@NotNull @Positive Long processingTimeoutMs,
		@NotNull @Positive Integer batchSize,
		@NotNull @Positive Long workerFixedDelayMs,
		@NotNull @Positive Long workerInitialDelayMs) {
}
