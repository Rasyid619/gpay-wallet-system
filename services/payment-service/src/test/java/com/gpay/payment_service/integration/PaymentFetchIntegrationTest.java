package com.gpay.payment_service.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for the authenticated GET /payments/{id} endpoint, covering the owner happy
 * path, the ownership guard returning 404, and the not-found case returning 404.
 */
class PaymentFetchIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final TopupTransactionRepository topupTransactionRepository;

	@Autowired
	PaymentFetchIntegrationTest(
			MockMvc mockMvc,
			TopupTransactionRepository topupTransactionRepository) {
		this.mockMvc = mockMvc;
		this.topupTransactionRepository = topupTransactionRepository;
	}

	private TopupTransaction seedPayment(UUID userId, UUID walletId, long amount) {
		TopupTransaction transaction = TopupTransaction.createPending(
				UUID.randomUUID(),
				userId,
				walletId,
				amount,
				"seed-key-" + UUID.randomUUID(),
				"trace-seed",
				Instant.now());
		return topupTransactionRepository.save(transaction);
	}

	@Test
	void returnsOwnedPaymentForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		TopupTransaction transaction = seedPayment(userId, walletId, 75_000L);

		mockMvc.perform(get("/payments/{id}", transaction.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payment_transaction_id").value(transaction.getId().toString()))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.amount").value(75000))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.created_at").exists());
	}

	@Test
	void returnsNotFoundWhenPaymentBelongsToAnotherUser() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		TopupTransaction transaction = seedPayment(ownerId, UUID.randomUUID(), 75_000L);

		mockMvc.perform(get("/payments/{id}", transaction.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(otherUserId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void returnsNotFoundWhenPaymentDoesNotExist() throws Exception {
		mockMvc.perform(get("/payments/{id}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}
}
