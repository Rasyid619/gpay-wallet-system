package com.gpay.payment_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kafka topic names used by the payment service outbox publishers.
 *
 * @param walletCreditCommandsTopic topic carrying wallet credit commands
 * @param topupSucceededTopic       topic carrying successful top-up events
 * @param topupFailedTopic          topic carrying failed top-up events
 */
@Validated
@ConfigurationProperties(prefix = "payment.kafka")
public record PaymentKafkaProperties(
		@NotBlank String walletCreditCommandsTopic,
		@NotBlank String topupSucceededTopic,
		@NotBlank String topupFailedTopic) {
}
