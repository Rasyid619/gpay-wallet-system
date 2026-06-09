package com.gpay.payment_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payment.webhook")
public record PaymentWebhookProperties(
		@NotBlank String gatewaySecret) {
}
