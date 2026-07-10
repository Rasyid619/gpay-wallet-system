package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.wallet_service.constant.OutboxEventType;
import com.gpay.wallet_service.constant.TransferStatus;
import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.entity.OutboxEvent;
import com.gpay.wallet_service.entity.Transfer;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for transfer-result outbox enqueueing, covering the completed and
 * failed event kinds and the duplicate-enqueue guard.
 */
@ExtendWith(MockitoExtension.class)
class WalletOutboxServiceTest {

	@Mock
	private OutboxEventRepository outboxEventRepository;

	private WalletOutboxService service() {
		return new WalletOutboxService(new ObjectMapper(), outboxEventRepository);
	}

	private Transfer transfer(TransferStatus status, String failureReason) {
		Instant now = Instant.now();
		Wallet sender = Wallet.create(UUID.randomUUID(), UUID.randomUUID(), 100L, WalletStatus.ACTIVE, now, now);
		Wallet receiver = Wallet.create(UUID.randomUUID(), UUID.randomUUID(), 0L, WalletStatus.ACTIVE, now, now);
		return Transfer.create(UUID.randomUUID(), sender, receiver, 40_000L, status, failureReason, now);
	}

	@Test
	void enqueuesSenderAndReceiverEventsForSuccessfulTransfer() {
		Transfer transfer = transfer(TransferStatus.SUCCESS, null);
		UUID senderUserId = UUID.randomUUID();
		UUID receiverUserId = UUID.randomUUID();
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transfer.getId(), OutboxEventType.TRANSFER_COMPLETED)).thenReturn(false);
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transfer.getId(), OutboxEventType.TRANSFER_RECEIVED)).thenReturn(false);

		service().enqueueTransferResult(transfer, senderUserId, receiverUserId, "trace-outbox", Instant.now());

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository, times(2)).save(captor.capture());
		OutboxEvent completedEvent = captor.getAllValues().get(0);
		assertThat(completedEvent.getEventType()).isEqualTo(OutboxEventType.TRANSFER_COMPLETED);
		assertThat(completedEvent.getAggregateId()).isEqualTo(transfer.getId());
		assertThat(completedEvent.getTraceId()).isEqualTo("trace-outbox");
		assertThat(completedEvent.getPayload()).contains(
				"\"transfer_id\":\"" + transfer.getId() + "\"",
				"\"user_id\":\"" + senderUserId + "\"",
				"\"amount\":40000");
		OutboxEvent receivedEvent = captor.getAllValues().get(1);
		assertThat(receivedEvent.getEventType()).isEqualTo(OutboxEventType.TRANSFER_RECEIVED);
		assertThat(receivedEvent.getAggregateId()).isEqualTo(transfer.getId());
		assertThat(receivedEvent.getPayload()).contains("\"user_id\":\"" + receiverUserId + "\"");
	}

	@Test
	void enqueuesOnlySenderFailedEventForFailedTransfer() {
		Transfer transfer = transfer(TransferStatus.FAILED, "INSUFFICIENT_BALANCE");
		UUID senderUserId = UUID.randomUUID();
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transfer.getId(), OutboxEventType.TRANSFER_FAILED)).thenReturn(false);

		service().enqueueTransferResult(transfer, senderUserId, UUID.randomUUID(), null, Instant.now());

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository, times(1)).save(captor.capture());
		OutboxEvent event = captor.getValue();
		assertThat(event.getEventType()).isEqualTo(OutboxEventType.TRANSFER_FAILED);
		assertThat(event.getPayload()).contains(
				"\"failure_reason\":\"INSUFFICIENT_BALANCE\"",
				"\"user_id\":\"" + senderUserId + "\"");
	}

	@Test
	void skipsEnqueueWhenBothEventsAlreadyExistForTransfer() {
		Transfer transfer = transfer(TransferStatus.SUCCESS, null);
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transfer.getId(), OutboxEventType.TRANSFER_COMPLETED)).thenReturn(true);
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transfer.getId(), OutboxEventType.TRANSFER_RECEIVED)).thenReturn(true);

		service().enqueueTransferResult(transfer, UUID.randomUUID(), UUID.randomUUID(), "trace", Instant.now());

		verify(outboxEventRepository, never()).save(any());
	}

	@Test
	void enqueuesOnlyMissingReceivedEventWhenCompletedAlreadyExists() {
		Transfer transfer = transfer(TransferStatus.SUCCESS, null);
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transfer.getId(), OutboxEventType.TRANSFER_COMPLETED)).thenReturn(true);
		when(outboxEventRepository.existsByAggregateIdAndEventType(
				transfer.getId(), OutboxEventType.TRANSFER_RECEIVED)).thenReturn(false);

		service().enqueueTransferResult(transfer, UUID.randomUUID(), UUID.randomUUID(), "trace", Instant.now());

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository, times(1)).save(captor.capture());
		assertThat(captor.getValue().getEventType()).isEqualTo(OutboxEventType.TRANSFER_RECEIVED);
	}
}
