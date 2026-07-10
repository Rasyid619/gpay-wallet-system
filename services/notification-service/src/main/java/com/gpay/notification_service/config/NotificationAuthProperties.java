package com.gpay.notification_service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Auth-service internal user lookup connection settings.
 *
 * @param userLookupUrl base URL of the internal user lookup endpoint
 * @param internalToken shared internal service token for the lookup
 * @param timeoutMs     lookup request timeout in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "notification.auth")
public record NotificationAuthProperties(
		@NotBlank String userLookupUrl,
		@NotBlank String internalToken,
		@NotNull @Positive Long timeoutMs) {
}
