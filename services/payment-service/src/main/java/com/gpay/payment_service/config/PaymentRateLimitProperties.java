package com.gpay.payment_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable payment top-up rate limit settings.
 *
 * @param maxRequestsPerMinute maximum top-up requests per user per minute
 */
@ConfigurationProperties(prefix = "payment.rate-limit")
public record PaymentRateLimitProperties(
		Integer maxRequestsPerMinute) {
}
