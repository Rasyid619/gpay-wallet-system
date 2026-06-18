package com.gpay.wallet_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kafka topic names consumed and produced by the wallet service.
 *
 * @param walletCreditCommandsTopic topic carrying wallet credit commands
 * @param deadLetterTopic           topic for commands that cannot be applied
 */
@Validated
@ConfigurationProperties(prefix = "wallet.kafka")
public record WalletKafkaProperties(
		@NotBlank String walletCreditCommandsTopic,
		@NotBlank String deadLetterTopic) {
}
