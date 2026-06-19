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
import com.gpay.payment_service.dto.ClaimedOutboxEvent;
import com.gpay.payment_service.dto.WalletCreditOutboxPayload;
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
 * Branch-focused unit tests for {@link PaymentOutboxWorker} covering the lost-claim
 * skip and the failure-message normalization branches without a database or broker.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOutboxWorkerBranchTest {

	@Mock
	private OutboxEventRepository outboxEventRepository;

	@Mock
	private PaymentOutboxStateService paymentOutboxStateService;

	@Mock
	private WalletCreditCommandPublisher walletCreditCommandPublisher;

	private PaymentOutboxProperties properties() {
		return new PaymentOutboxProperties(
				60_000L, 10, 86_400_000L, 300_000L, 10, 3_600_000L, 3_600_000L);
	}

	private PaymentOutboxWorker worker() {
		return new PaymentOutboxWorker(
				outboxEventRepository,
				paymentOutboxStateService,
				properties(),
				walletCreditCommandPublisher);
	}

	private OutboxEvent pendingEvent() {
		return OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.CREDIT_WALLET_REQUESTED,
				UUID.randomUUID(),
				"{}",
				Instant.now());
	}

	private void stubDueEvent(OutboxEvent event) {
		when(outboxEventRepository.findStaleProcessingEvents(
				eq(OutboxEventType.CREDIT_WALLET_REQUESTED),
				eq(OutboxEventStatus.PROCESSING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of());
		when(outboxEventRepository.findDueEvents(
				eq(OutboxEventType.CREDIT_WALLET_REQUESTED),
				eq(OutboxEventStatus.PENDING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of(event));
	}

	@Test
	void skipsEventWhenClaimIsLost() throws Exception {
		OutboxEvent event = pendingEvent();
		stubDueEvent(event);
		when(paymentOutboxStateService.claim(event.getId())).thenReturn(null);

		worker().processPendingWalletCredits();

		verify(walletCreditCommandPublisher, never())
				.publish(any(WalletCreditOutboxPayload.class), any(String.class), any(String.class));
	}

	@Test
	void normalizesBlankFailureMessageOnPublishError() throws Exception {
		OutboxEvent event = pendingEvent();
		ClaimedOutboxEvent claimed = new ClaimedOutboxEvent(
				event.getId(),
				new WalletCreditOutboxPayload(UUID.randomUUID(), event.getAggregateId(), 75_000L),
				"trace");
		stubDueEvent(event);
		when(paymentOutboxStateService.claim(event.getId())).thenReturn(claimed);
		doThrow(new RuntimeException("   "))
				.when(walletCreditCommandPublisher)
				.publish(any(WalletCreditOutboxPayload.class), any(String.class), any(String.class));

		worker().processPendingWalletCredits();

		verify(paymentOutboxStateService).recordFailedAttempt(
				eq(event.getId()),
				eq("Wallet credit delivery failed"),
				eq(true));
	}

	@Test
	void truncatesLongFailureMessageOnPublishError() throws Exception {
		OutboxEvent event = pendingEvent();
		ClaimedOutboxEvent claimed = new ClaimedOutboxEvent(
				event.getId(),
				new WalletCreditOutboxPayload(UUID.randomUUID(), event.getAggregateId(), 75_000L),
				"trace");
		String longMessage = "x".repeat(900);
		stubDueEvent(event);
		when(paymentOutboxStateService.claim(event.getId())).thenReturn(claimed);
		doThrow(new RuntimeException(longMessage))
				.when(walletCreditCommandPublisher)
				.publish(any(WalletCreditOutboxPayload.class), any(String.class), any(String.class));

		worker().processPendingWalletCredits();

		verify(paymentOutboxStateService).recordFailedAttempt(
				eq(event.getId()),
				ArgumentMatchers.argThat(error -> error.length() == 500),
				eq(true));
	}
}
