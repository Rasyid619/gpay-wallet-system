package com.gpay.payment_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.repository.OutboxEventRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for the unauthenticated gateway webhook, covering HMAC accept/reject, the
 * SUCCESS path that creates one wallet-credit and one topup-succeeded outbox event, the FAILED
 * path that creates only a topup-failed event, and the not-found / mismatch failure branches.
 */
class PaymentWebhookIntegrationTest extends AbstractIntegrationTest {

	private static final String TIMESTAMP = "2026-06-09T10:00:00Z";

	private final MockMvc mockMvc;
	private final TopupTransactionRepository topupTransactionRepository;
	private final OutboxEventRepository outboxEventRepository;

	@Autowired
	PaymentWebhookIntegrationTest(
			MockMvc mockMvc,
			TopupTransactionRepository topupTransactionRepository,
			OutboxEventRepository outboxEventRepository) {
		this.mockMvc = mockMvc;
		this.topupTransactionRepository = topupTransactionRepository;
		this.outboxEventRepository = outboxEventRepository;
	}

	private TopupTransaction seedPendingTransaction() {
		Instant now = Instant.now();
		return topupTransactionRepository.save(TopupTransaction.createPending(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				75_000L,
				"webhook-key-" + UUID.randomUUID(),
				"trace-webhook",
				now));
	}

	private String webhookBody(TopupTransaction transaction, String status, String gatewayReference) {
		return """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":75000,"status":"%s","gateway_reference":"%s"}"""
				.formatted(transaction.getId(), transaction.getWalletId(), status, gatewayReference);
	}

	@Test
	void successWebhookMarksTransactionSuccessAndCreatesOneOutboxEvent() throws Exception {
		TopupTransaction transaction = seedPendingTransaction();
		String body = webhookBody(transaction, "SUCCESS", "gw-success");

		mockMvc.perform(post("/payments/webhook/gateway")
						.header("X-Gateway-Signature", signWebhook(TIMESTAMP, body))
						.header("X-Gateway-Timestamp", TIMESTAMP)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payment_transaction_id").value(transaction.getId().toString()))
				.andExpect(jsonPath("$.status").value("SUCCESS"));

		assertThat(topupTransactionRepository.findById(transaction.getId()).orElseThrow().getStatus().name())
				.isEqualTo("SUCCESS");
		assertThat(outboxEventRepository.existsByAggregateIdAndEventType(
				transaction.getId(), OutboxEventType.CREDIT_WALLET_REQUESTED)).isTrue();
		assertThat(outboxEventRepository.existsByAggregateIdAndEventType(
				transaction.getId(), OutboxEventType.TOPUP_SUCCEEDED)).isTrue();
		assertThat(outboxEventRepository.count()).isEqualTo(2);
	}

	@Test
	void failedWebhookMarksTransactionFailedAndCreatesOnlyTopupFailedEvent() throws Exception {
		TopupTransaction transaction = seedPendingTransaction();
		String body = webhookBody(transaction, "FAILED", "gw-failed");

		mockMvc.perform(post("/payments/webhook/gateway")
						.header("X-Gateway-Signature", signWebhook(TIMESTAMP, body))
						.header("X-Gateway-Timestamp", TIMESTAMP)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"));

		assertThat(topupTransactionRepository.findById(transaction.getId()).orElseThrow().getStatus().name())
				.isEqualTo("FAILED");
		assertThat(outboxEventRepository.existsByAggregateIdAndEventType(
				transaction.getId(), OutboxEventType.TOPUP_FAILED)).isTrue();
		assertThat(outboxEventRepository.existsByAggregateIdAndEventType(
				transaction.getId(), OutboxEventType.CREDIT_WALLET_REQUESTED)).isFalse();
		assertThat(outboxEventRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsWebhookWithInvalidSignatureWithoutMutatingTransaction() throws Exception {
		TopupTransaction transaction = seedPendingTransaction();
		String body = webhookBody(transaction, "SUCCESS", "gw-tampered");

		mockMvc.perform(post("/payments/webhook/gateway")
						.header("X-Gateway-Signature", "deadbeef")
						.header("X-Gateway-Timestamp", TIMESTAMP)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("INVALID_WEBHOOK_SIGNATURE"));

		assertThat(topupTransactionRepository.findById(transaction.getId()).orElseThrow().getStatus().name())
				.isEqualTo("PENDING");
		assertThat(outboxEventRepository.count()).isZero();
	}

	@Test
	void returnsNotFoundWhenTransactionDoesNotExist() throws Exception {
		String body = """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":75000,"status":"SUCCESS","gateway_reference":"gw-missing"}"""
				.formatted(UUID.randomUUID(), UUID.randomUUID());

		mockMvc.perform(post("/payments/webhook/gateway")
						.header("X-Gateway-Signature", signWebhook(TIMESTAMP, body))
						.header("X-Gateway-Timestamp", TIMESTAMP)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void returnsBadRequestWhenWebhookAmountDoesNotMatchTransaction() throws Exception {
		TopupTransaction transaction = seedPendingTransaction();
		String body = """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":99999,"status":"SUCCESS","gateway_reference":"gw-mismatch"}"""
				.formatted(transaction.getId(), transaction.getWalletId());

		mockMvc.perform(post("/payments/webhook/gateway")
						.header("X-Gateway-Signature", signWebhook(TIMESTAMP, body))
						.header("X-Gateway-Timestamp", TIMESTAMP)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

		assertThat(topupTransactionRepository.findById(transaction.getId()).orElseThrow().getStatus().name())
				.isEqualTo("PENDING");
		assertThat(outboxEventRepository.count()).isZero();
	}
}
