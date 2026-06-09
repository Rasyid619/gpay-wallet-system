package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.gpay.payment_service.dto.IdempotentResponse;
import com.gpay.payment_service.dto.TopUpRequest;
import com.gpay.payment_service.dto.TopUpResponse;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.exception.BadRequestException;
import com.gpay.payment_service.exception.IdempotencyConflictException;
import com.gpay.payment_service.repository.IdempotencyKeyRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for payment top-up creation and durable idempotency.
 */
@SpringBootTest
class PaymentTopUpServiceTest {

	@Autowired
	private IdempotencyKeyRepository idempotencyKeyRepository;

	@Autowired
	private PaymentTopUpService paymentTopUpService;

	@Autowired
	private TopupTransactionRepository topupTransactionRepository;

	@Test
	void createsPendingTopUpTransactionAndStoresIdempotencyResponse() {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		String idempotencyKey = "topup-create-" + UUID.randomUUID();

		IdempotentResponse result = paymentTopUpService.topUp(
				userId,
				idempotencyKey,
				new TopUpRequest(walletId, 75000L),
				"trace-topup");

		assertThat(result.status()).isEqualTo(201);
		assertThat(result.body()).isInstanceOf(TopUpResponse.class);
		TopUpResponse body = (TopUpResponse) result.body();
		assertThat(body.walletId()).isEqualTo(walletId);
		assertThat(body.amount()).isEqualTo(75000L);
		assertThat(body.status()).isEqualTo("PENDING");

		TopupTransaction transaction = topupTransactionRepository.findById(body.paymentTransactionId()).orElseThrow();
		assertThat(transaction.getUserId()).isEqualTo(userId);
		assertThat(transaction.getWalletId()).isEqualTo(walletId);
		assertThat(transaction.getAmount()).isEqualTo(75000L);
		assertThat(transaction.getStatus().name()).isEqualTo("PENDING");
		assertThat(transaction.getIdempotencyKey()).isEqualTo(idempotencyKey);
		assertThat(transaction.getTraceId()).isEqualTo("trace-topup");
		assertThat(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).isPresent();
	}

	@Test
	void replaysSameIdempotencyKeyAndPayloadWithoutCreatingAnotherTransaction() {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		String idempotencyKey = "topup-replay-" + UUID.randomUUID();
		TopUpRequest request = new TopUpRequest(walletId, 75000L);
		long countBefore = topupTransactionRepository.count();

		IdempotentResponse first = paymentTopUpService.topUp(userId, idempotencyKey, request, "trace-first");
		IdempotentResponse second = paymentTopUpService.topUp(userId, idempotencyKey, request, "trace-second");

		TopUpResponse firstBody = (TopUpResponse) first.body();
		assertThat(second.status()).isEqualTo(201);
		assertThat(second.body()).isInstanceOf(JsonNode.class);
		JsonNode secondBody = (JsonNode) second.body();
		assertThat(secondBody.get("payment_transaction_id").asText()).isEqualTo(firstBody.paymentTransactionId().toString());
		assertThat(secondBody.get("wallet_id").asText()).isEqualTo(walletId.toString());
		assertThat(secondBody.get("amount").asLong()).isEqualTo(75000L);
		assertThat(topupTransactionRepository.count()).isEqualTo(countBefore + 1);
	}

	@Test
	void allowsSameIdempotencyKeyForDifferentUsers() {
		String idempotencyKey = "topup-shared-key-" + UUID.randomUUID();

		IdempotentResponse first = paymentTopUpService.topUp(
				UUID.randomUUID(),
				idempotencyKey,
				new TopUpRequest(UUID.randomUUID(), 75000L),
				"trace-first");
		IdempotentResponse second = paymentTopUpService.topUp(
				UUID.randomUUID(),
				idempotencyKey,
				new TopUpRequest(UUID.randomUUID(), 75000L),
				"trace-second");

		assertThat(first.status()).isEqualTo(201);
		assertThat(second.status()).isEqualTo(201);
	}

	@Test
	void rejectsSameIdempotencyKeyWithDifferentPayloadForSameUser() {
		UUID userId = UUID.randomUUID();
		String idempotencyKey = "topup-conflict-" + UUID.randomUUID();

		paymentTopUpService.topUp(
				userId,
				idempotencyKey,
				new TopUpRequest(UUID.randomUUID(), 75000L),
				"trace-conflict");

		assertThatThrownBy(() -> paymentTopUpService.topUp(
				userId,
				idempotencyKey,
				new TopUpRequest(UUID.randomUUID(), 75000L),
				"trace-conflict"))
				.isInstanceOf(IdempotencyConflictException.class);
	}

	@Test
	void rejectsBlankIdempotencyKey() {
		assertThatThrownBy(() -> paymentTopUpService.topUp(
				UUID.randomUUID(),
				" ",
				new TopUpRequest(UUID.randomUUID(), 75000L),
				"trace-blank"))
				.isInstanceOf(BadRequestException.class);
	}
}
