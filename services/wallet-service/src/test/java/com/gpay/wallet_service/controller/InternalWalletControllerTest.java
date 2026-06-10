package com.gpay.wallet_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.wallet_service.config.SecurityConfig;
import com.gpay.wallet_service.config.TraceIdFilter;
import com.gpay.wallet_service.dto.IdempotentResponse;
import com.gpay.wallet_service.dto.InternalWalletCreditRequest;
import com.gpay.wallet_service.dto.InternalWalletCreditResponse;
import com.gpay.wallet_service.dto.InternalWalletProvisionRequest;
import com.gpay.wallet_service.dto.InternalWalletProvisionResponse;
import com.gpay.wallet_service.exception.GlobalExceptionHandler;
import com.gpay.wallet_service.exception.InternalAuthenticationException;
import com.gpay.wallet_service.security.JwtAuthFilter;
import com.gpay.wallet_service.security.JwtService;
import com.gpay.wallet_service.service.InternalWalletCreditService;
import com.gpay.wallet_service.service.InternalWalletProvisionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MVC tests for internal wallet credit access.
 */
@WebMvcTest(InternalWalletController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class, TraceIdFilter.class})
@TestPropertySource(properties = "jwt.secret=test-secret-minimum-32-characters-long")
class InternalWalletControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InternalWalletCreditService internalWalletCreditService;

	@MockitoBean
	private InternalWalletProvisionService internalWalletProvisionService;

	@Test
	void returnsProvisionResponseForInternalRequest() throws Exception {
		UUID walletId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-06-09T04:00:00Z");
		when(internalWalletProvisionService.provision(
				eq("internal-token"),
				any(InternalWalletProvisionRequest.class)))
				.thenReturn(new InternalWalletProvisionResponse(
						walletId,
						userId,
						0L,
						"ACTIVE",
						createdAt));

		mockMvc.perform(post("/internal/wallets/provision")
						.header("X-Internal-Token", "internal-token")
						.header("X-Trace-Id", "trace-provision")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "user_id": "%s"
								}
								""".formatted(userId)))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Trace-Id", "trace-provision"))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.user_id").value(userId.toString()))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.created_at").value("2026-06-09T04:00:00Z"));
	}

	@Test
	void returnsCreditResponseForInternalRequest() throws Exception {
		UUID walletId = UUID.randomUUID();
		UUID paymentTransactionId = UUID.randomUUID();
		Instant creditedAt = Instant.parse("2026-06-09T04:00:00Z");
		when(internalWalletCreditService.credit(
				eq("internal-token"),
				eq("credit-key-1"),
				any(InternalWalletCreditRequest.class),
				eq("trace-credit")))
				.thenReturn(new IdempotentResponse(
						200,
						new InternalWalletCreditResponse(
								walletId,
								paymentTransactionId,
								50000L,
								150000L,
								creditedAt)));

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", "internal-token")
						.header("Idempotency-Key", "credit-key-1")
						.header("X-Trace-Id", "trace-credit")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "payment_transaction_id": "%s",
								  "amount": 50000
								}
								""".formatted(walletId, paymentTransactionId)))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Trace-Id", "trace-credit"))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.payment_transaction_id").value(paymentTransactionId.toString()))
				.andExpect(jsonPath("$.amount").value(50000))
				.andExpect(jsonPath("$.balance_after").value(150000))
				.andExpect(jsonPath("$.credited_at").value("2026-06-09T04:00:00Z"));
	}

	@Test
	void returnsBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", "internal-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "payment_transaction_id": "%s",
								  "amount": 50000
								}
								""".formatted(UUID.randomUUID(), UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(header().exists("X-Trace-Id"))
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.trace_id").exists());

		verifyNoInteractions(internalWalletCreditService);
	}

	@Test
	void returnsUnauthorizedWhenInternalTokenIsInvalid() throws Exception {
		UUID walletId = UUID.randomUUID();
		UUID paymentTransactionId = UUID.randomUUID();
		when(internalWalletCreditService.credit(
				eq("wrong-token"),
				eq("credit-key-invalid"),
				any(InternalWalletCreditRequest.class),
				any()))
				.thenThrow(new InternalAuthenticationException("Internal authentication is invalid"));

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", "wrong-token")
						.header("Idempotency-Key", "credit-key-invalid")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "wallet_id": "%s",
								  "payment_transaction_id": "%s",
								  "amount": 50000
								}
								""".formatted(walletId, paymentTransactionId)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}
}
