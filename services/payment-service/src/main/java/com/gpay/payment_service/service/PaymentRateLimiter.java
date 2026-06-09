package com.gpay.payment_service.service;

import com.gpay.payment_service.config.PaymentRateLimitProperties;
import com.gpay.payment_service.exception.RateLimitExceededException;
import com.gpay.payment_service.exception.RateLimitUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/* Enforces Redis-backed per-user payment top-up rate limits. */
@Service
@RequiredArgsConstructor
public class PaymentRateLimiter {

	private static final DateTimeFormatter WINDOW_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
	private static final Duration WINDOW_TTL = Duration.ofSeconds(60);

	private final Clock clock;
	private final PaymentRateLimitProperties properties;
	private final StringRedisTemplate redisTemplate;

	/**
	 * Checks and increments the current user's payment top-up request count.
	 *
	 * @param userId authenticated user identifier
	 * @throws RateLimitExceededException    when user exceeds the configured per-minute limit
	 * @throws RateLimitUnavailableException when Redis cannot verify the rate limit safely
	 */
	public void checkTopUpAllowed(UUID userId) {
		String key = buildKey(userId);
		Long count = increment(key);
		if (count == null) {
			throw new RateLimitUnavailableException("Payment rate limit cannot be verified");
		}

		if (count == 1L) {
			expire(key);
		}

		if (count > properties.maxRequestsPerMinute()) {
			throw new RateLimitExceededException(
					"Maximum " + properties.maxRequestsPerMinute() + " payment requests per minute allowed");
		}
	}

	private Long increment(String key) {
		try {
			return redisTemplate.opsForValue().increment(key);
		} catch (RedisConnectionFailureException ex) {
			throw new RateLimitUnavailableException("Payment rate limit cannot be verified");
		}
	}

	private void expire(String key) {
		try {
			Boolean expirationSet = redisTemplate.expire(key, WINDOW_TTL);
			if (!Boolean.TRUE.equals(expirationSet)) {
				throw new RateLimitUnavailableException("Payment rate limit cannot be verified");
			}
		} catch (RedisConnectionFailureException ex) {
			throw new RateLimitUnavailableException("Payment rate limit cannot be verified");
		}
	}

	private String buildKey(UUID userId) {
		String window = LocalDateTime.now(clock).format(WINDOW_FORMATTER);
		return "rate-limit:payment:" + userId + ":" + window;
	}
}
