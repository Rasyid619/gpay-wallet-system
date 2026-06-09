package com.gpay.payment_service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payment.outbox")
public record PaymentOutboxProperties(
		@NotNull URI walletCreditUrl,
		@NotBlank String walletInternalToken,
		@NotNull @Positive Long requestTimeoutMs,
		@NotNull @Positive Long retryDelayMs,
		@NotNull @Positive Long processingTimeoutMs,
		@NotNull @Positive Integer batchSize,
		@NotNull @Positive Long workerFixedDelayMs,
		@NotNull @Positive Long workerInitialDelayMs) {
}
