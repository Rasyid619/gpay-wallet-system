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
import java.time.Duration;
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
		"spring.kafka.bootstrap-servers=localhost:9092",
		"payment.kafka.wallet-credit-commands-topic=wallet.credit.commands",
		"payment.outbox.retry-delay-ms=60000",
		"payment.outbox.max-attempts=3",
		"payment.outbox.max-age-ms=86400000",
		"payment.outbox.processing-timeout-ms=300000",
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
	private WalletCreditCommandPublisher walletCreditCommandPublisher;

	@Test
	void successfulPublishMarksOutboxEventProcessed() throws Exception {
		TopupTransaction transaction = pendingTransaction("trace-outbox-success");
		OutboxEvent event = pendingOutboxEvent(transaction);

		paymentOutboxWorker.processPendingWalletCredits();

		ArgumentCaptor<WalletCreditOutboxPayload> payloadCaptor = ArgumentCaptor.forClass(WalletCreditOutboxPayload.class);
		verify(walletCreditCommandPublisher).publish(
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
	void publishFailureRemainsRetryableWithFailureMetadata() throws Exception {
		TopupTransaction transaction = pendingTransaction("trace-outbox-failure");
		OutboxEvent event = pendingOutboxEvent(transaction);
		doThrow(new RuntimeException("broker unavailable"))
				.when(walletCreditCommandPublisher)
				.publish(any(WalletCreditOutboxPayload.class), any(String.class), any(String.class));

		paymentOutboxWorker.processPendingWalletCredits();

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PENDING");
		assertThat(updated.getRetryCount()).isEqualTo(1);
		assertThat(updated.getLastError()).isEqualTo("broker unavailable");
		assertThat(updated.getNextRetryAt()).isAfter(Instant.now());
	}

	@Test
	void exhaustedRetriesMarkOutboxEventFailed() throws Exception {
		TopupTransaction transaction = pendingTransaction("trace-outbox-exhausted");
		OutboxEvent event = pendingOutboxEvent(transaction);
		Instant past = Instant.now().minusSeconds(1);
		event.markFailedAttempt("attempt-1", past, past);
		event.markFailedAttempt("attempt-2", past, past);
		outboxEventRepository.saveAndFlush(event);
		doThrow(new RuntimeException("broker still unavailable"))
				.when(walletCreditCommandPublisher)
				.publish(any(WalletCreditOutboxPayload.class), any(String.class), any(String.class));

		paymentOutboxWorker.processPendingWalletCredits();

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("FAILED");
		assertThat(updated.getRetryCount()).isEqualTo(3);
		assertThat(updated.getLastError()).isEqualTo("broker still unavailable");
		assertThat(updated.getNextRetryAt()).isNull();
	}

	@Test
	void eventOlderThanMaxAgeMarkedFailedWhileRetriesRemain() throws Exception {
		TopupTransaction transaction = pendingTransaction("trace-outbox-aged");
		OutboxEvent event = pendingOutboxEvent(transaction, Instant.now().minus(Duration.ofHours(25)));
		doThrow(new RuntimeException("broker unavailable"))
				.when(walletCreditCommandPublisher)
				.publish(any(WalletCreditOutboxPayload.class), any(String.class), any(String.class));

		paymentOutboxWorker.processPendingWalletCredits();

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("FAILED");
		assertThat(updated.getRetryCount()).isEqualTo(1);
		assertThat(updated.getNextRetryAt()).isNull();
	}

	@Test
	void staleProcessingEventIsRecoveredAndPublished() throws Exception {
		TopupTransaction transaction = pendingTransaction("trace-stale-processing");
		OutboxEvent event = pendingOutboxEvent(transaction);
		event.markProcessing(Instant.now().minusSeconds(600));
		outboxEventRepository.saveAndFlush(event);

		paymentOutboxWorker.processPendingWalletCredits();

		verify(walletCreditCommandPublisher).publish(
				any(WalletCreditOutboxPayload.class),
				eq("payment-outbox-" + event.getId()),
				eq("trace-stale-processing"));
		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PROCESSED");
		assertThat(updated.getRetryCount()).isEqualTo(1);
		assertThat(updated.getLastError()).isNull();
		assertThat(updated.getNextRetryAt()).isNull();
	}

	private TopupTransaction pendingTransaction(String traceId) {
		return topupTransactionRepository.save(TopupTransaction.createPending(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				75000L,
				"outbox-worker-key-" + UUID.randomUUID(),
				traceId,
				Instant.now()));
	}

	private OutboxEvent pendingOutboxEvent(TopupTransaction transaction) {
		return pendingOutboxEvent(transaction, Instant.now());
	}

	private OutboxEvent pendingOutboxEvent(TopupTransaction transaction, Instant createdAt) {
		WalletCreditOutboxPayload payload = new WalletCreditOutboxPayload(
				transaction.getWalletId(),
				transaction.getId(),
				transaction.getAmount());
		return outboxEventRepository.save(OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.CREDIT_WALLET_REQUESTED,
				transaction.getId(),
				writeJson(payload),
				createdAt));
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize test outbox payload", ex);
		}
	}
}
