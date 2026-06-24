package com.gpay.wallet_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.common.security.SecurityAutoConfiguration;
import com.gpay.common.tracing.TraceIdFilter;
import com.gpay.wallet_service.config.SecurityConfig;
import com.gpay.wallet_service.dto.IdempotentResponse;
import com.gpay.wallet_service.dto.TransferRequest;
import com.gpay.wallet_service.dto.TransferResponse;
import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.dto.WalletMutationPageResponse;
import com.gpay.wallet_service.dto.WalletMutationResponse;
import com.gpay.wallet_service.exception.GlobalExceptionHandler;
import com.gpay.wallet_service.exception.WalletNotFoundException;
import com.gpay.wallet_service.service.WalletBalanceService;
import com.gpay.wallet_service.service.WalletMutationService;
import com.gpay.wallet_service.service.WalletTransferService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MVC tests for authenticated wallet endpoint access.
 */
@WebMvcTest(WalletController.class)
@Import({SecurityConfig.class, SecurityAutoConfiguration.class, GlobalExceptionHandler.class, TraceIdFilter.class})
@TestPropertySource(properties = "jwt.secret=test-secret-minimum-32-characters-long")
class WalletControllerTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WalletBalanceService walletBalanceService;

	@MockitoBean
	private WalletMutationService walletMutationService;

	@MockitoBean
	private WalletTransferService walletTransferService;

	@Test
	void returnsBalanceForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		when(walletBalanceService.getBalance(userId)).thenReturn(new WalletBalanceResponse(walletId, 125000L));

		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.balance").value(125000));
	}

	@Test
	void returnsUnauthorizedWhenTokenIsMissing() throws Exception {
		mockMvc.perform(get("/wallets/balance"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().exists("X-Trace-Id"))
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.trace_id").exists());

		verifyNoInteractions(walletBalanceService);
	}

	@Test
	void returnsUnauthorizedWhenTokenIsInvalid() throws Exception {
		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
						.header("X-Trace-Id", "trace-invalid-wallet"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("X-Trace-Id", "trace-invalid-wallet"))
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.trace_id").value("trace-invalid-wallet"));

		verifyNoInteractions(walletBalanceService);
	}

	@Test
	void returnsNotFoundWhenAuthenticatedUserHasNoWallet() throws Exception {
		UUID userId = UUID.randomUUID();
		when(walletBalanceService.getBalance(userId))
				.thenThrow(new WalletNotFoundException("Wallet was not found for authenticated user"));

		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Wallet was not found for authenticated user"));
	}

	@Test
	void returnsPaginatedMutationsForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID mutationId = UUID.randomUUID();
		UUID transactionId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-06-09T02:15:30Z");
		WalletMutationResponse mutation = new WalletMutationResponse(
				mutationId,
				"CREDIT",
				"TOP_UP",
				50000L,
				150000L,
				transactionId,
				createdAt);
		when(walletMutationService.getMutations(eq(userId), any(Pageable.class)))
				.thenReturn(new WalletMutationPageResponse(1, 2, 3L, 2, List.of(mutation)));

		mockMvc.perform(get("/wallets/mutations?page=1&size=2")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.total_items").value(3))
				.andExpect(jsonPath("$.total_pages").value(2))
				.andExpect(jsonPath("$.items[0].mutation_id").value(mutationId.toString()))
				.andExpect(jsonPath("$.items[0].type").value("CREDIT"))
				.andExpect(jsonPath("$.items[0].source").value("TOP_UP"))
				.andExpect(jsonPath("$.items[0].amount").value(50000))
				.andExpect(jsonPath("$.items[0].balance_after").value(150000))
				.andExpect(jsonPath("$.items[0].related_transaction_id").value(transactionId.toString()))
				.andExpect(jsonPath("$.items[0].created_at").value("2026-06-09T02:15:30Z"));
	}

	@Test
	void returnsUnauthorizedForMutationsWhenTokenIsMissing() throws Exception {
		mockMvc.perform(get("/wallets/mutations"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(walletMutationService);
	}

	@Test
	void returnsUnauthorizedForMutationsWhenTokenIsInvalid() throws Exception {
		mockMvc.perform(get("/wallets/mutations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(walletMutationService);
	}

	@Test
	void returnsTransferResponseForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID transferId = UUID.randomUUID();
		UUID senderWalletId = UUID.randomUUID();
		UUID receiverWalletId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-06-09T03:00:00Z");
		when(walletTransferService.transfer(
				eq(userId),
				eq("transfer-key-1"),
				any(TransferRequest.class),
				eq("trace-1")))
				.thenReturn(new IdempotentResponse(
						200,
						new TransferResponse(
								transferId,
								senderWalletId,
								receiverWalletId,
								25000L,
								"SUCCESS",
								createdAt)));

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "transfer-key-1")
						.header("X-Trace-Id", "trace-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "receiver_wallet_id": "%s",
								  "amount": 25000
								}
								""".formatted(receiverWalletId)))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Trace-Id", "trace-1"))
				.andExpect(jsonPath("$.transfer_id").value(transferId.toString()))
				.andExpect(jsonPath("$.sender_wallet_id").value(senderWalletId.toString()))
				.andExpect(jsonPath("$.receiver_wallet_id").value(receiverWalletId.toString()))
				.andExpect(jsonPath("$.amount").value(25000))
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.created_at").value("2026-06-09T03:00:00Z"));
	}

	@Test
	void returnsBadRequestWhenTransferIdempotencyKeyIsMissing() throws Exception {
		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "receiver_wallet_id": "%s",
								  "amount": 25000
								}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(header().exists("X-Trace-Id"))
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.trace_id").exists());

		verifyNoInteractions(walletTransferService);
	}

	@Test
	void returnsUnauthorizedForTransferWhenTokenIsMissing() throws Exception {
		mockMvc.perform(post("/wallets/transfer")
						.header("Idempotency-Key", "transfer-key-missing-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "receiver_wallet_id": "%s",
								  "amount": 25000
								}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(walletTransferService);
	}

	@Test
	void returnsUnauthorizedForTransferWhenTokenIsInvalid() throws Exception {
		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
						.header("Idempotency-Key", "transfer-key-invalid-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "receiver_wallet_id": "%s",
								  "amount": 25000
								}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(walletTransferService);
	}

	private String createToken(UUID userId) {
		Instant now = Instant.now();
		SecretKey signingKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
		return Jwts.builder()
				.subject(userId.toString())
				.claim("email", "user@example.com")
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(900)))
				.signWith(signingKey)
				.compact();
	}
}
