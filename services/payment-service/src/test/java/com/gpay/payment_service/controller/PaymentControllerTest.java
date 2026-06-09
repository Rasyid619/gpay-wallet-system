package com.gpay.payment_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.payment_service.config.SecurityConfig;
import com.gpay.payment_service.dto.GatewayWebhookResponse;
import com.gpay.payment_service.dto.IdempotentResponse;
import com.gpay.payment_service.dto.TopUpRequest;
import com.gpay.payment_service.dto.TopUpResponse;
import com.gpay.payment_service.exception.GlobalExceptionHandler;
import com.gpay.payment_service.exception.RateLimitExceededException;
import com.gpay.payment_service.exception.RateLimitUnavailableException;
import com.gpay.payment_service.security.JwtAuthFilter;
import com.gpay.payment_service.security.JwtService;
import com.gpay.payment_service.service.PaymentTopUpService;
import com.gpay.payment_service.service.PaymentWebhookService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MVC tests for authenticated payment top-up endpoint access.
 */
@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
		"jwt.secret=test-secret-minimum-32-characters-long",
		"payment.gateway.top-up-url=http://localhost:8084/mock-gateway/top-up",
		"payment.gateway.timeout-ms=5000",
		"payment.webhook.gateway-secret=test-gateway-webhook-secret",
		"payment.outbox.wallet-credit-url=http://localhost:8082/internal/wallets/credit",
		"payment.outbox.wallet-internal-token=test-internal-token",
		"payment.outbox.request-timeout-ms=5000",
		"payment.outbox.retry-delay-ms=60000",
		"payment.outbox.batch-size=10",
		"payment.outbox.worker-fixed-delay-ms=3600000",
		"payment.outbox.worker-initial-delay-ms=3600000"
})
class PaymentControllerTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PaymentTopUpService paymentTopUpService;

	@MockitoBean
	private PaymentWebhookService paymentWebhookService;

	@Test
	void acceptsGatewayWebhookWithoutJwt() throws Exception {
		UUID paymentTransactionId = UUID.randomUUID();
		when(paymentWebhookService.processGatewayWebhook(
				eq("signature"),
				eq("2026-06-09T10:00:00Z"),
				any(String.class)))
				.thenReturn(new GatewayWebhookResponse(paymentTransactionId, "SUCCESS"));

		mockMvc.perform(post("/payments/webhook/gateway")
						.header("X-Gateway-Signature", "signature")
						.header("X-Gateway-Timestamp", "2026-06-09T10:00:00Z")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "payment_transaction_id": "%s",
								  "wallet_id": "%s",
								  "amount": 75000,
								  "status": "SUCCESS",
								  "gateway_reference": "gw-reference"
								}
								""".formatted(paymentTransactionId, UUID.randomUUID())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payment_transaction_id").value(paymentTransactionId.toString()))
				.andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	@Test
	void returnsBadRequestWhenGatewaySignatureHeaderIsMissing() throws Exception {
		mockMvc.perform(post("/payments/webhook/gateway")
						.header("X-Gateway-Timestamp", "2026-06-09T10:00:00Z")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}

	@Test
	void returnsCreatedTopUpForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		UUID paymentTransactionId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-06-09T09:30:00Z");
		when(paymentTopUpService.topUp(
				eq(userId),
				eq("topup-key-1"),
				any(TopUpRequest.class),
				eq("trace-payment")))
				.thenReturn(new IdempotentResponse(
						201,
						new TopUpResponse(paymentTransactionId, walletId, 75000L, "PENDING", createdAt)));

		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "topup-key-1")
						.header("X-Trace-Id", "trace-payment")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "amount": 75000,
								  "gateway_mode": "SUCCESS"
								}
								""".formatted(walletId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.payment_transaction_id").value(paymentTransactionId.toString()))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.amount").value(75000))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.created_at").value("2026-06-09T09:30:00Z"));
	}

	@Test
	void returnsUnauthorizedWhenTokenIsMissing() throws Exception {
		mockMvc.perform(post("/payments/top-up")
						.header("Idempotency-Key", "topup-key-missing-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "amount": 75000,
								  "gateway_mode": "SUCCESS"
								}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(paymentTopUpService);
	}

	@Test
	void returnsUnauthorizedWhenTokenIsInvalid() throws Exception {
		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
						.header("Idempotency-Key", "topup-key-invalid-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "amount": 75000,
								  "gateway_mode": "SUCCESS"
								}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(paymentTopUpService);
	}

	@Test
	void returnsBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "amount": 75000,
								  "gateway_mode": "SUCCESS"
								}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

		verifyNoInteractions(paymentTopUpService);
	}

	@Test
	void returnsBadRequestWhenAmountIsInvalid() throws Exception {
		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID()))
						.header("Idempotency-Key", "topup-key-invalid-amount")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "amount": 0,
								  "gateway_mode": "SUCCESS"
								}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

		verifyNoInteractions(paymentTopUpService);
	}

	@Test
	void returnsTooManyRequestsWhenRateLimitIsExceeded() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		when(paymentTopUpService.topUp(
				eq(userId),
				eq("topup-key-rate-limit"),
				any(TopUpRequest.class),
				eq(null)))
				.thenThrow(new RateLimitExceededException("Maximum 5 payment requests per minute allowed"));

		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "topup-key-rate-limit")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "amount": 75000,
								  "gateway_mode": "SUCCESS"
								}
								""".formatted(walletId)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.message").value("Maximum 5 payment requests per minute allowed"));
	}

	@Test
	void returnsServiceUnavailableWhenRateLimitCannotBeVerified() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		when(paymentTopUpService.topUp(
				eq(userId),
				eq("topup-key-redis-down"),
				any(TopUpRequest.class),
				eq(null)))
				.thenThrow(new RateLimitUnavailableException("Payment rate limit cannot be verified"));

		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "topup-key-redis-down")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "amount": 75000,
								  "gateway_mode": "SUCCESS"
								}
								""".formatted(walletId)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("RATE_LIMIT_UNAVAILABLE"))
				.andExpect(jsonPath("$.message").value("Payment rate limit cannot be verified"));
	}

	private String createToken(UUID userId) {
		SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(userId.toString())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(900)))
				.signWith(key)
				.compact();
	}
}
