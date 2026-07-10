package com.gpay.notification_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Kafka topic names consumed by the notification service.
 *
 * @param transferCompletedTopic topic carrying sender-side successful transfer events
 * @param transferReceivedTopic  topic carrying receiver-side successful transfer events
 * @param transferFailedTopic    topic carrying failed transfer events
 * @param topupSucceededTopic    topic carrying successful top-up events
 * @param topupFailedTopic       topic carrying failed top-up events
 * @param deadLetterTopic        topic for events that cannot be handled
 */
@Validated
@ConfigurationProperties(prefix = "notification.kafka")
public record NotificationKafkaProperties(
		@NotBlank String transferCompletedTopic,
		@NotBlank String transferReceivedTopic,
		@NotBlank String transferFailedTopic,
		@NotBlank String topupSucceededTopic,
		@NotBlank String topupFailedTopic,
		@NotBlank String deadLetterTopic) {
}
