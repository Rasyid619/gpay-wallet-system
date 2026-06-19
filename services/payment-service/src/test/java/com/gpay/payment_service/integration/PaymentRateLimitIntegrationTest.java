package com.gpay.payment_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.gpay.payment_service.service.PaymentGatewayClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for Redis-backed top-up rate limiting against a real Redis container: requests
 * within the per-minute ceiling are allowed and the request that exceeds it is denied with 429.
 */
class PaymentRateLimitIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final StringRedisTemplate redisTemplate;

	@MockitoBean
	private PaymentGatewayClient paymentGatewayClient;

	@Autowired
	PaymentRateLimitIntegrationTest(MockMvc mockMvc, StringRedisTemplate redisTemplate) {
		this.mockMvc = mockMvc;
		this.redisTemplate = redisTemplate;
	}

	@BeforeEach
	void flushRedis() {
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
	}

	private String topUpBody(UUID walletId) {
		return """
				{
				  "wallet_id": "%s",
				  "amount": 75000,
				  "gateway_mode": "SUCCESS"
				}
				""".formatted(walletId);
	}

	@Test
	void allowsRequestsWithinLimitAndDeniesOnceTheCeilingIsExceeded() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = "Bearer " + createToken(userId);

		// The rate-limit key is bucketed per wall-clock minute. Firing 2 * ceiling + 1 requests
		// guarantees that even if the burst straddles a single minute rollover, one window still
		// receives at least ceiling + 1 requests, so a deny is deterministic and not boundary-fragile.
		int totalRequests = (2 * RATE_LIMIT_MAX_REQUESTS_PER_MINUTE) + 1;
		int allowed = 0;
		int denied = 0;
		for (int i = 0; i < totalRequests; i++) {
			MockHttpServletResponse response = mockMvc.perform(post("/payments/top-up")
							.header(HttpHeaders.AUTHORIZATION, token)
							.header("Idempotency-Key", "rate-" + i)
							.contentType(MediaType.APPLICATION_JSON)
							.content(topUpBody(UUID.randomUUID())))
					.andReturn()
					.getResponse();

			if (response.getStatus() == 201) {
				allowed++;
				continue;
			}

			assertThat(response.getStatus()).isEqualTo(429);
			assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
			denied++;
		}

		assertThat(allowed).isGreaterThanOrEqualTo(1);
		assertThat(denied).isGreaterThanOrEqualTo(1);
	}
}
