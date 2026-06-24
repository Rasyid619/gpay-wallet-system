package com.gpay.payment_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.common.security.SecurityAutoConfiguration;
import com.gpay.common.tracing.TraceIdFilter;
import com.gpay.payment_service.config.SecurityConfig;
import com.gpay.payment_service.dto.TopUpResponse;
import com.gpay.payment_service.exception.GlobalExceptionHandler;
import com.gpay.payment_service.exception.NotFoundException;
import com.gpay.payment_service.service.PaymentTopUpService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/* MVC tests for admin payment lookup access control and response shaping. */
@WebMvcTest(AdminPaymentController.class)
@Import({SecurityConfig.class, SecurityAutoConfiguration.class, GlobalExceptionHandler.class, TraceIdFilter.class})
@TestPropertySource(properties = {
		"jwt.secret=test-secret-minimum-32-characters-long",
		"payment.gateway.top-up-url=http://localhost:8084/mock-gateway/top-up",
		"payment.gateway.timeout-ms=5000",
		"payment.webhook.gateway-secret=test-gateway-webhook-secret",
		"payment.outbox.retry-delay-ms=60000",
		"payment.outbox.max-attempts=10",
		"payment.outbox.max-age-ms=86400000",
		"payment.outbox.processing-timeout-ms=300000",
		"payment.outbox.batch-size=10",
		"payment.outbox.worker-fixed-delay-ms=3600000",
		"payment.outbox.worker-initial-delay-ms=3600000"
})
class AdminPaymentControllerTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PaymentTopUpService paymentTopUpService;

	@Test
	void returnsPaymentForAdmin() throws Exception {
		UUID paymentId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-06-09T09:30:00Z");
		when(paymentTopUpService.fetchPayment(any(), eq(paymentId), any()))
				.thenReturn(new TopUpResponse(paymentId, walletId, 75000L, "PENDING", createdAt));

		mockMvc.perform(get("/admin/payments/{id}", paymentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payment_transaction_id").value(paymentId.toString()))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.amount").value(75000))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.created_at").value("2026-06-09T09:30:00Z"));
	}

	@Test
	void passesAuthenticatedAdminAndTraceToLookup() throws Exception {
		UUID paymentId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		when(paymentTopUpService.fetchPayment(any(), eq(paymentId), any()))
				.thenReturn(new TopUpResponse(paymentId, UUID.randomUUID(), 75000L, "PENDING", Instant.now()));

		mockMvc.perform(get("/admin/payments/{id}", paymentId)
						.header("X-Trace-Id", "trace-admin-123")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(adminId, "ADMIN")))
				.andExpect(status().isOk());

		verify(paymentTopUpService).fetchPayment(adminId, paymentId, "trace-admin-123");
	}

	@Test
	void returnsForbiddenForNonAdmin() throws Exception {
		mockMvc.perform(get("/admin/payments/{id}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "USER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"))
				.andExpect(jsonPath("$.trace_id").exists());

		verifyNoInteractions(paymentTopUpService);
	}

	@Test
	void returnsNotFoundWhenPaymentMissing() throws Exception {
		UUID paymentId = UUID.randomUUID();
		when(paymentTopUpService.fetchPayment(any(), eq(paymentId), any()))
				.thenThrow(new NotFoundException("Payment transaction was not found"));

		mockMvc.perform(get("/admin/payments/{id}", paymentId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "ADMIN")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	private String createToken(UUID userId, String role) {
		SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(userId.toString())
				.claim("role", role)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(900)))
				.signWith(key)
				.compact();
	}
}
