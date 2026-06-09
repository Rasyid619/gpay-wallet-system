package com.gpay.payment_service.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payment.gateway")
public record PaymentGatewayProperties(
		@NotNull URI topUpUrl,
		@NotNull @Positive Long timeoutMs) {
}
