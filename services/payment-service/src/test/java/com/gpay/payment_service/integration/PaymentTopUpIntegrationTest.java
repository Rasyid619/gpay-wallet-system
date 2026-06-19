package com.gpay.payment_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.payment_service.dto.TopUpRequest;
import com.gpay.payment_service.repository.IdempotencyKeyRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import com.gpay.payment_service.service.PaymentGatewayClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for the authenticated top-up endpoint up to the mocked gateway-call boundary,
 * covering the created/PENDING happy path, the after-commit gateway dispatch, idempotency replay,
 * idempotency conflict, and the missing-token guard.
 */
class PaymentTopUpIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final TopupTransactionRepository topupTransactionRepository;
	private final IdempotencyKeyRepository idempotencyKeyRepository;

	@MockitoBean
	private PaymentGatewayClient paymentGatewayClient;

	@Autowired
	PaymentTopUpIntegrationTest(
			MockMvc mockMvc,
			TopupTransactionRepository topupTransactionRepository,
			IdempotencyKeyRepository idempotencyKeyRepository) {
		this.mockMvc = mockMvc;
		this.topupTransactionRepository = topupTransactionRepository;
		this.idempotencyKeyRepository = idempotencyKeyRepository;
	}

	private String topUpBody(UUID walletId, long amount) {
		return """
				{
				  "wallet_id": "%s",
				  "amount": %d,
				  "gateway_mode": "SUCCESS"
				}
				""".formatted(walletId, amount);
	}

	@Test
	void createsPendingTopUpAndDispatchesGatewayAfterCommit() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();

		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "topup-create")
						.header("X-Trace-Id", "trace-create")
						.contentType(MediaType.APPLICATION_JSON)
						.content(topUpBody(walletId, 75_000L)))
				.andExpect(status().isCreated())
				.andExpect(header().string("X-Trace-Id", "trace-create"))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.amount").value(75000))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.payment_transaction_id").exists())
				.andExpect(jsonPath("$.created_at").exists());

		assertThat(topupTransactionRepository.count()).isEqualTo(1);
		assertThat(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, "topup-create")).isPresent();
		verify(paymentGatewayClient, timeout(2000))
				.requestTopUp(any(UUID.class), any(TopUpRequest.class), eq("trace-create"));
	}

	@Test
	void replaysStoredResponseForRepeatedKeyAndPayloadWithoutDoubleEffect() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		String token = "Bearer " + createToken(userId);
		String body = topUpBody(walletId, 75_000L);

		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, token)
						.header("Idempotency-Key", "topup-replay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, token)
						.header("Idempotency-Key", "topup-replay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));

		assertThat(topupTransactionRepository.count()).isEqualTo(1);
		assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsReusedKeyWithDifferentPayload() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		String token = "Bearer " + createToken(userId);

		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, token)
						.header("Idempotency-Key", "topup-conflict")
						.contentType(MediaType.APPLICATION_JSON)
						.content(topUpBody(walletId, 75_000L)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/payments/top-up")
						.header(HttpHeaders.AUTHORIZATION, token)
						.header("Idempotency-Key", "topup-conflict")
						.contentType(MediaType.APPLICATION_JSON)
						.content(topUpBody(walletId, 10_000L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_CONFLICT"));

		assertThat(topupTransactionRepository.count()).isEqualTo(1);
		assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsTopUpWhenTokenIsMissing() throws Exception {
		mockMvc.perform(post("/payments/top-up")
						.header("Idempotency-Key", "topup-no-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content(topUpBody(UUID.randomUUID(), 75_000L)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

		assertThat(topupTransactionRepository.count()).isZero();
		verifyNoInteractions(paymentGatewayClient);
	}
}
