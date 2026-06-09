package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.payment_service.config.PaymentRateLimitProperties;
import com.gpay.payment_service.exception.RateLimitExceededException;
import com.gpay.payment_service.exception.RateLimitUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for Redis-backed payment rate limiting.
 */
class PaymentRateLimiterTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-06-09T10:15:30Z"), ZoneId.of("UTC"));
	private final PaymentRateLimitProperties properties = new PaymentRateLimitProperties(5);
	private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
	private final PaymentRateLimiter rateLimiter = new PaymentRateLimiter(clock, properties, redisTemplate);

	@Test
	void allowsRequestWithinLimitAndExpiresNewWindow() {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		String expectedKey = "rate-limit:payment:" + userId + ":202606091015";
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(expectedKey)).thenReturn(1L);
		when(redisTemplate.expire(expectedKey, Duration.ofSeconds(60))).thenReturn(true);

		rateLimiter.checkTopUpAllowed(userId);

		verify(valueOperations).increment(expectedKey);
		verify(redisTemplate).expire(expectedKey, Duration.ofSeconds(60));
	}

	@Test
	void rejectsRequestOverLimit() {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
		String expectedKey = "rate-limit:payment:" + userId + ":202606091015";
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(expectedKey)).thenReturn(6L);

		assertThatThrownBy(() -> rateLimiter.checkTopUpAllowed(userId))
				.isInstanceOf(RateLimitExceededException.class)
				.hasMessageContaining("Maximum 5 payment requests per minute allowed");
	}

	@Test
	void failsClosedWhenRedisIncrementFails() {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000003");
		String expectedKey = "rate-limit:payment:" + userId + ":202606091015";
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(expectedKey)).thenThrow(new RedisConnectionFailureException("redis down"));

		assertThatThrownBy(() -> rateLimiter.checkTopUpAllowed(userId))
				.isInstanceOf(RateLimitUnavailableException.class)
				.hasMessageContaining("Payment rate limit cannot be verified");
	}

	@Test
	void failsClosedWhenRedisExpireFailsForNewWindow() {
		UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000004");
		String expectedKey = "rate-limit:payment:" + userId + ":202606091015";
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment(expectedKey)).thenReturn(1L);
		when(redisTemplate.expire(expectedKey, Duration.ofSeconds(60))).thenReturn(false);

		assertThatThrownBy(() -> rateLimiter.checkTopUpAllowed(userId))
				.isInstanceOf(RateLimitUnavailableException.class)
				.hasMessageContaining("Payment rate limit cannot be verified");
	}
}
