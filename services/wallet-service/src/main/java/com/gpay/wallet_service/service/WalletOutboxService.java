package com.gpay.wallet_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.wallet_service.constant.OutboxEventType;
import com.gpay.wallet_service.constant.TransferStatus;
import com.gpay.wallet_service.dto.TransferEventOutboxPayload;
import com.gpay.wallet_service.entity.OutboxEvent;
import com.gpay.wallet_service.entity.Transfer;
import com.gpay.wallet_service.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/* Enqueues durable transfer-result events alongside the transfer transaction. */
@Service
@RequiredArgsConstructor
public class WalletOutboxService {

	private final ObjectMapper objectMapper;
	private final OutboxEventRepository outboxEventRepository;

	/**
	 * Writes the pending transfer-result outbox events in the caller's transaction.
	 *
	 * <p>A successful transfer enqueues a sent event for the sender and a received
	 * event for the receiver; a failed transfer enqueues only the sender's failed
	 * event, since no money reached the receiver.
	 *
	 * @param transfer       persisted transfer with its final status
	 * @param senderUserId   auth-service user id of the transfer initiator
	 * @param receiverUserId auth-service user id of the receiving wallet owner
	 * @param traceId        request trace identifier when supplied
	 * @param now            enqueue timestamp
	 */
	public void enqueueTransferResult(
			Transfer transfer,
			UUID senderUserId,
			UUID receiverUserId,
			String traceId,
			Instant now) {
		if (transfer.getStatus() != TransferStatus.SUCCESS) {
			enqueueEvent(transfer, OutboxEventType.TRANSFER_FAILED, senderUserId, traceId, now);
			return;
		}

		enqueueEvent(transfer, OutboxEventType.TRANSFER_COMPLETED, senderUserId, traceId, now);
		enqueueEvent(transfer, OutboxEventType.TRANSFER_RECEIVED, receiverUserId, traceId, now);
	}

	private void enqueueEvent(
			Transfer transfer,
			OutboxEventType eventType,
			UUID recipientUserId,
			String traceId,
			Instant now) {
		if (outboxEventRepository.existsByAggregateIdAndEventType(transfer.getId(), eventType)) {
			return;
		}

		TransferEventOutboxPayload payload = new TransferEventOutboxPayload(
				transfer.getId(),
				transfer.getSenderWallet().getId(),
				transfer.getReceiverWallet().getId(),
				recipientUserId,
				transfer.getAmount(),
				transfer.getFailureReason());
		outboxEventRepository.save(OutboxEvent.createPending(
				UUID.randomUUID(),
				eventType,
				transfer.getId(),
				writeJson(payload),
				traceId,
				now));
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Outbox payload could not be serialized", ex);
		}
	}
}
