package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.dto.TopupEventOutboxPayload;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.repository.OutboxEventRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import com.gpay.payment_service.support.PaymentTestContainers;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for the top-up event outbox worker, covering acked
 * publishes for both event kinds and the retryable-failure path against the
 * real schema.
 */
@SpringBootTest
class PaymentTopupEventWorkerTest {

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		PaymentTestContainers.registerProperties(registry);
	}

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private PaymentTopupEventWorker paymentTopupEventWorker;

	@Autowired
	private TopupTransactionRepository topupTransactionRepository;

	@MockitoBean
	private TopupEventPublisher topupEventPublisher;

	@Test
	void successfulPublishMarksTopupSucceededEventProcessed() throws Exception {
		TopupTransaction transaction = pendingTransaction("trace-topup-success");
		OutboxEvent event = pendingOutboxEvent(transaction, OutboxEventType.TOPUP_SUCCEEDED);

		paymentTopupEventWorker.processPendingTopupEvents();

		ArgumentCaptor<TopupEventOutboxPayload> payloadCaptor =
				ArgumentCaptor.forClass(TopupEventOutboxPayload.class);
		verify(topupEventPublisher).publish(
				eq(OutboxEventType.TOPUP_SUCCEEDED),
				payloadCaptor.capture(),
				eq("payment-outbox-" + event.getId()),
				eq("trace-topup-success"));
		assertThat(payloadCaptor.getValue().paymentTransactionId()).isEqualTo(transaction.getId());
		assertThat(payloadCaptor.getValue().userId()).isEqualTo(transaction.getUserId());
		assertThat(payloadCaptor.getValue().amount()).isEqualTo(transaction.getAmount());

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PROCESSED");
	}

	@Test
	void successfulPublishMarksTopupFailedEventProcessed() throws Exception {
		TopupTransaction transaction = pendingTransaction("trace-topup-failed");
		OutboxEvent event = pendingOutboxEvent(transaction, OutboxEventType.TOPUP_FAILED);

		paymentTopupEventWorker.processPendingTopupEvents();

		verify(topupEventPublisher).publish(
				eq(OutboxEventType.TOPUP_FAILED),
				any(TopupEventOutboxPayload.class),
				eq("payment-outbox-" + event.getId()),
				eq("trace-topup-failed"));
		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PROCESSED");
	}

	@Test
	void publishFailureRemainsRetryableWithFailureMetadata() throws Exception {
		TopupTransaction transaction = pendingTransaction("trace-topup-retry");
		OutboxEvent event = pendingOutboxEvent(transaction, OutboxEventType.TOPUP_SUCCEEDED);
		doThrow(new RuntimeException("broker unavailable"))
				.when(topupEventPublisher)
				.publish(any(), any(TopupEventOutboxPayload.class), any(String.class), any());

		paymentTopupEventWorker.processPendingTopupEvents();

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PENDING");
		assertThat(updated.getRetryCount()).isEqualTo(1);
		assertThat(updated.getLastError()).isEqualTo("broker unavailable");
		assertThat(updated.getNextRetryAt()).isAfter(Instant.now());
	}

	private TopupTransaction pendingTransaction(String traceId) {
		return topupTransactionRepository.save(TopupTransaction.createPending(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				75000L,
				"topup-worker-key-" + UUID.randomUUID(),
				traceId,
				Instant.now()));
	}

	private OutboxEvent pendingOutboxEvent(TopupTransaction transaction, OutboxEventType eventType) {
		TopupEventOutboxPayload payload = new TopupEventOutboxPayload(
				transaction.getId(),
				transaction.getUserId(),
				transaction.getWalletId(),
				transaction.getAmount(),
				transaction.getFailureReason());
		return outboxEventRepository.save(OutboxEvent.createPending(
				UUID.randomUUID(),
				eventType,
				transaction.getId(),
				writeJson(payload),
				Instant.now()));
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize test outbox payload", ex);
		}
	}
}
