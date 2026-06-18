package com.gpay.payment_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kafka topic names used by the payment service outbox publisher.
 *
 * @param walletCreditCommandsTopic topic carrying wallet credit commands
 */
@Validated
@ConfigurationProperties(prefix = "payment.kafka")
public record PaymentKafkaProperties(@NotBlank String walletCreditCommandsTopic) {
}
