package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for top-up result outbox enqueueing, covering the succeeded and
 * failed event kinds and the duplicate-enqueue guard.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOutboxServiceTest {

	@Mock
	private OutboxEventRepository outboxEventRepository;

	private PaymentOutboxService service() {
		return new PaymentOutboxService(new ObjectMapper(), outboxEventRepository);
	}

	private TopupTransaction pendingTransaction() {
		return TopupTransaction.createPending(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				75_000L,
				"topup-key",
				"trace-topup",
				Instant.now());
	}

	@Test
	void enqueuesTopupSucceededEventForSuccessfulTopup() {
		TopupTransaction transaction = pendingTransaction();
		transaction.markSuccess("gw-ref", Instant.now());
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transaction.getId(), OutboxEventType.TOPUP_SUCCEEDED)).thenReturn(false);

		service().enqueueTopupResult(transaction, Instant.now());

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(captor.capture());
		OutboxEvent event = captor.getValue();
		assertThat(event.getEventType()).isEqualTo(OutboxEventType.TOPUP_SUCCEEDED);
		assertThat(event.getAggregateId()).isEqualTo(transaction.getId());
		assertThat(event.getPayload()).contains(
				"\"payment_transaction_id\":\"" + transaction.getId() + "\"",
				"\"user_id\":\"" + transaction.getUserId() + "\"",
				"\"amount\":75000");
	}

	@Test
	void enqueuesTopupFailedEventWithFailureReason() {
		TopupTransaction transaction = pendingTransaction();
		transaction.markFailed("gw-ref", "Gateway reported payment failure", Instant.now());
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transaction.getId(), OutboxEventType.TOPUP_FAILED)).thenReturn(false);

		service().enqueueTopupResult(transaction, Instant.now());

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(captor.capture());
		OutboxEvent event = captor.getValue();
		assertThat(event.getEventType()).isEqualTo(OutboxEventType.TOPUP_FAILED);
		assertThat(event.getPayload()).contains("\"failure_reason\":\"Gateway reported payment failure\"");
	}

	@Test
	void skipsEnqueueWhenTopupEventAlreadyExists() {
		TopupTransaction transaction = pendingTransaction();
		transaction.markSuccess("gw-ref", Instant.now());
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transaction.getId(), OutboxEventType.TOPUP_SUCCEEDED)).thenReturn(true);

		service().enqueueTopupResult(transaction, Instant.now());

		verify(outboxEventRepository, never()).save(any());
	}
}
