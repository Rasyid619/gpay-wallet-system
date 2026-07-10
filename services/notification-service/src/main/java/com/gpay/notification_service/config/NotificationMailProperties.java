package com.gpay.notification_service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SMTP connection and sender identity for outgoing notification emails.
 *
 * @param host        SMTP server host
 * @param port        SMTP server port
 * @param fromAddress sender address on outgoing emails
 */
@Validated
@ConfigurationProperties(prefix = "notification.mail")
public record NotificationMailProperties(
		@NotBlank String host,
		@NotNull @Positive Integer port,
		@NotBlank String fromAddress) {
}
