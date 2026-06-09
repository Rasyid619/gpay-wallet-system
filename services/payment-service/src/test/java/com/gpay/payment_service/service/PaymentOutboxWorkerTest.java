package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.dto.WalletCreditOutboxPayload;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.repository.OutboxEventRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(properties = {
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
class PaymentOutboxWorkerTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private PaymentOutboxWorker paymentOutboxWorker;

	@Autowired
	private TopupTransactionRepository topupTransactionRepository;

	@MockitoBean
	private WalletCreditClient walletCreditClient;

	@Test
	void successfulDeliveryMarksOutboxEventProcessed() {
		TopupTransaction transaction = pendingTransaction("trace-outbox-success");
		OutboxEvent event = pendingOutboxEvent(transaction);

		paymentOutboxWorker.processPendingWalletCredits();

		ArgumentCaptor<WalletCreditOutboxPayload> payloadCaptor = ArgumentCaptor.forClass(WalletCreditOutboxPayload.class);
		verify(walletCreditClient).creditWallet(
				payloadCaptor.capture(),
				eq("payment-outbox-" + event.getId()),
				eq("trace-outbox-success"));
		assertThat(payloadCaptor.getValue().walletId()).isEqualTo(transaction.getWalletId());
		assertThat(payloadCaptor.getValue().paymentTransactionId()).isEqualTo(transaction.getId());
		assertThat(payloadCaptor.getValue().amount()).isEqualTo(transaction.getAmount());

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PROCESSED");
		assertThat(updated.getLastError()).isNull();
		assertThat(updated.getNextRetryAt()).isNull();
	}

	@Test
	void failedDeliveryRemainsRetryableWithFailureMetadata() {
		TopupTransaction transaction = pendingTransaction("trace-outbox-failure");
		OutboxEvent event = pendingOutboxEvent(transaction);
		doThrow(new RuntimeException("wallet service unavailable"))
				.when(walletCreditClient)
				.creditWallet(any(WalletCreditOutboxPayload.class), any(String.class), any(String.class));

		paymentOutboxWorker.processPendingWalletCredits();

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PENDING");
		assertThat(updated.getRetryCount()).isEqualTo(1);
		assertThat(updated.getLastError()).isEqualTo("wallet service unavailable");
		assertThat(updated.getNextRetryAt()).isAfter(Instant.now());
	}

	private TopupTransaction pendingTransaction(String traceId) {
		Instant now = Instant.parse("2026-06-09T10:00:00Z");
		return topupTransactionRepository.save(TopupTransaction.createPending(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				75000L,
				"outbox-worker-key-" + UUID.randomUUID(),
				traceId,
				now));
	}

	private OutboxEvent pendingOutboxEvent(TopupTransaction transaction) {
		Instant now = Instant.parse("2026-06-09T10:00:00Z");
		WalletCreditOutboxPayload payload = new WalletCreditOutboxPayload(
				transaction.getWalletId(),
				transaction.getId(),
				transaction.getAmount());
		return outboxEventRepository.save(OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.CREDIT_WALLET_REQUESTED,
				transaction.getId(),
				writeJson(payload),
				now));
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize test outbox payload", ex);
		}
	}
}
