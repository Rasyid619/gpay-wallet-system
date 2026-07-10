package com.gpay.notification_service.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retry budget for notification delivery attempts.
 *
 * @param maxAttempts attempts after which a retryable failure becomes FAILED
 */
@Validated
@ConfigurationProperties(prefix = "notification.retry")
public record NotificationRetryProperties(@NotNull @Positive Integer maxAttempts) {
}
