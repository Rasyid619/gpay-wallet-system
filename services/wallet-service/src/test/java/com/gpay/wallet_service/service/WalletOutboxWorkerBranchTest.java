package com.gpay.wallet_service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.wallet_service.config.WalletOutboxProperties;
import com.gpay.wallet_service.constant.OutboxEventStatus;
import com.gpay.wallet_service.constant.OutboxEventType;
import com.gpay.wallet_service.dto.ClaimedTransferOutboxEvent;
import com.gpay.wallet_service.dto.TransferEventOutboxPayload;
import com.gpay.wallet_service.entity.OutboxEvent;
import com.gpay.wallet_service.repository.OutboxEventRepository;
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
 * Branch-focused unit tests for {@link WalletOutboxWorker} covering the
 * lost-claim skip, stale recovery, and the failure-message normalization
 * branches without a database or broker.
 */
@ExtendWith(MockitoExtension.class)
class WalletOutboxWorkerBranchTest {

	@Mock
	private OutboxEventRepository outboxEventRepository;

	@Mock
	private TransferEventPublisher transferEventPublisher;

	@Mock
	private WalletOutboxStateService walletOutboxStateService;

	private WalletOutboxProperties properties() {
		return new WalletOutboxProperties(
				60_000L, 10, 86_400_000L, 300_000L, 10, 3_600_000L, 3_600_000L);
	}

	private WalletOutboxWorker worker() {
		return new WalletOutboxWorker(
				outboxEventRepository,
				transferEventPublisher,
				properties(),
				walletOutboxStateService);
	}

	private OutboxEvent pendingEvent() {
		return OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.TRANSFER_COMPLETED,
				UUID.randomUUID(),
				"{}",
				"trace",
				Instant.now());
	}

	private ClaimedTransferOutboxEvent claimedEvent(OutboxEvent event) {
		return new ClaimedTransferOutboxEvent(
				event.getId(),
				OutboxEventType.TRANSFER_COMPLETED,
				new TransferEventOutboxPayload(
						event.getAggregateId(),
						UUID.randomUUID(),
						UUID.randomUUID(),
						UUID.randomUUID(),
						75_000L,
						null),
				"trace");
	}

	private void stubDueEvent(OutboxEvent event) {
		when(outboxEventRepository.findStaleProcessingEvents(
				eq(OutboxEventStatus.PROCESSING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of());
		when(outboxEventRepository.findDueEvents(
				eq(OutboxEventStatus.PENDING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of(event));
	}

	@Test
	void skipsEventWhenClaimIsLost() throws Exception {
		OutboxEvent event = pendingEvent();
		stubDueEvent(event);
		when(walletOutboxStateService.claim(event.getId())).thenReturn(null);

		worker().processPendingTransferEvents();

		verify(transferEventPublisher, never())
				.publish(any(), any(TransferEventOutboxPayload.class), any(String.class), any());
	}

	@Test
	void recoversStaleProcessingEventsBeforeProcessing() {
		OutboxEvent staleEvent = pendingEvent();
		when(outboxEventRepository.findStaleProcessingEvents(
				eq(OutboxEventStatus.PROCESSING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of(staleEvent));
		when(outboxEventRepository.findDueEvents(
				eq(OutboxEventStatus.PENDING),
				any(Instant.class),
				any(Pageable.class)))
				.thenReturn(List.of());

		worker().processPendingTransferEvents();

		verify(walletOutboxStateService).recoverStaleProcessing(eq(staleEvent.getId()), any(Instant.class));
	}

	@Test
	void normalizesBlankFailureMessageOnPublishError() throws Exception {
		OutboxEvent event = pendingEvent();
		stubDueEvent(event);
		when(walletOutboxStateService.claim(event.getId())).thenReturn(claimedEvent(event));
		doThrow(new RuntimeException("   "))
				.when(transferEventPublisher)
				.publish(any(), any(TransferEventOutboxPayload.class), any(String.class), any());

		worker().processPendingTransferEvents();

		verify(walletOutboxStateService).recordFailedAttempt(
				eq(event.getId()),
				eq("Transfer event delivery failed"),
				eq(true));
	}

	@Test
	void normalizesNullFailureMessageOnPublishError() throws Exception {
		OutboxEvent event = pendingEvent();
		stubDueEvent(event);
		when(walletOutboxStateService.claim(event.getId())).thenReturn(claimedEvent(event));
		doThrow(new RuntimeException((String) null))
				.when(transferEventPublisher)
				.publish(any(), any(TransferEventOutboxPayload.class), any(String.class), any());

		worker().processPendingTransferEvents();

		verify(walletOutboxStateService).recordFailedAttempt(
				eq(event.getId()),
				eq("Transfer event delivery failed"),
				eq(true));
	}

	@Test
	void truncatesLongFailureMessageOnPublishError() throws Exception {
		OutboxEvent event = pendingEvent();
		String longMessage = "x".repeat(900);
		stubDueEvent(event);
		when(walletOutboxStateService.claim(event.getId())).thenReturn(claimedEvent(event));
		doThrow(new RuntimeException(longMessage))
				.when(transferEventPublisher)
				.publish(any(), any(TransferEventOutboxPayload.class), any(String.class), any());

		worker().processPendingTransferEvents();

		verify(walletOutboxStateService).recordFailedAttempt(
				eq(event.getId()),
				ArgumentMatchers.argThat(error -> error.length() == 500),
				eq(true));
	}
}
