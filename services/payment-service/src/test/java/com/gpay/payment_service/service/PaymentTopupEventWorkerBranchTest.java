package com.gpay.payment_service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.payment_service.config.PaymentOutboxProperties;
import com.gpay.payment_service.constant.OutboxEventStatus;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.dto.ClaimedTopupOutboxEvent;
import com.gpay.payment_service.dto.TopupEventOutboxPayload;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Branch-focused unit tests for {@link PaymentTopupEventWorker} covering the
 * lost-claim skip and the failure-message normalization branches without a
 * database or broker.
 */
@ExtendWith(MockitoExtension.class)
class PaymentTopupEventWorkerBranchTest {

	@Mock
	private OutboxEventRepository outboxEventRepository;

	@Mock
	private PaymentOutboxStateService paymentOutboxStateService;

	@Mock
	private TopupEventPublisher topupEventPublisher;

	private PaymentOutboxProperties properties() {
		return new PaymentOutboxProperties(
				60_000L, 10, 86_400_000L, 300_000L, 10, 3_600_000L, 3_600_000L);
	}

	private PaymentTopupEventWorker worker() {
		return new PaymentTopupEventWorker(
				outboxEventRepository,
				properties(),
				paymentOutboxStateService,
				topupEventPublisher);
	}

	private OutboxEvent pendingEvent() {
		return OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.TOPUP_SUCCEEDED,
				UUID.randomUUID(),
				"{}",
				Instant.now());
	}

	private ClaimedTopupOutboxEvent claimedEvent(OutboxEvent event) {
		return new ClaimedTopupOutboxEvent(
				event.getId(),
				OutboxEventType.TOPUP_SUCCEEDED,
				new TopupEventOutboxPayload(
						event.getAggregateId(), UUID.randomUUID(), UUID.randomUUID(), 75_000L, null),
				"trace");
	}

	private void stubDueEvent(OutboxEvent event) {
		when(outboxEventRepository.findStaleProcessingEvents(
				any(OutboxEventType.class),
				eq(OutboxEventStatus.PROCESSING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of());
		when(outboxEventRepository.findDueEvents(
				eq(OutboxEventType.TOPUP_SUCCEEDED),
				eq(OutboxEventStatus.PENDING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of(event));
		when(outboxEventRepository.findDueEvents(
				eq(OutboxEventType.TOPUP_FAILED),
				eq(OutboxEventStatus.PENDING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of());
	}

	@Test
	void skipsEventWhenClaimIsLost() throws Exception {
		OutboxEvent event = pendingEvent();
		stubDueEvent(event);
		when(paymentOutboxStateService.claimTopupEvent(event.getId())).thenReturn(null);

		worker().processPendingTopupEvents();

		verify(topupEventPublisher, never())
				.publish(any(), any(TopupEventOutboxPayload.class), any(String.class), any());
	}

	@Test
	void recoversStaleProcessingEventsBeforeProcessing() {
		OutboxEvent staleEvent = pendingEvent();
		when(outboxEventRepository.findStaleProcessingEvents(
				any(OutboxEventType.class),
				eq(OutboxEventStatus.PROCESSING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of(staleEvent), List.of());
		when(outboxEventRepository.findDueEvents(
				any(OutboxEventType.class),
				eq(OutboxEventStatus.PENDING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of());

		worker().processPendingTopupEvents();

		verify(paymentOutboxStateService).recoverStaleProcessing(eq(staleEvent.getId()), any(Instant.class));
	}

	@Test
	void normalizesBlankFailureMessageOnPublishError() throws Exception {
		OutboxEvent event = pendingEvent();
		stubDueEvent(event);
		when(paymentOutboxStateService.claimTopupEvent(event.getId())).thenReturn(claimedEvent(event));
		doThrow(new RuntimeException("   "))
				.when(topupEventPublisher)
				.publish(any(), any(TopupEventOutboxPayload.class), any(String.class), any());

		worker().processPendingTopupEvents();

		verify(paymentOutboxStateService).recordFailedAttempt(
				eq(event.getId()),
				eq("Top-up event delivery failed"),
				eq(true));
	}

	@Test
	void normalizesNullFailureMessageOnPublishError() throws Exception {
		OutboxEvent event = pendingEvent();
		stubDueEvent(event);
		when(paymentOutboxStateService.claimTopupEvent(event.getId())).thenReturn(claimedEvent(event));
		doThrow(new RuntimeException((String) null))
				.when(topupEventPublisher)
				.publish(any(), any(TopupEventOutboxPayload.class), any(String.class), any());

		worker().processPendingTopupEvents();

		verify(paymentOutboxStateService).recordFailedAttempt(
				eq(event.getId()),
				eq("Top-up event delivery failed"),
				eq(true));
	}

	@Test
	void truncatesLongFailureMessageOnPublishError() throws Exception {
		OutboxEvent event = pendingEvent();
		String longMessage = "x".repeat(900);
		stubDueEvent(event);
		when(paymentOutboxStateService.claimTopupEvent(event.getId())).thenReturn(claimedEvent(event));
		doThrow(new RuntimeException(longMessage))
				.when(topupEventPublisher)
				.publish(any(), any(TopupEventOutboxPayload.class), any(String.class), any());

		worker().processPendingTopupEvents();

		verify(paymentOutboxStateService).recordFailedAttempt(
				eq(event.getId()),
				ArgumentMatchers.argThat(error -> error.length() == 500),
				eq(true));
	}
}
