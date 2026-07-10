package com.gpay.wallet_service.dto;

import com.gpay.wallet_service.constant.OutboxEventType;
import java.util.UUID;

/**
 * Outbox event claimed for publishing, with its deserialized payload.
 *
 * @param eventId   outbox event identifier
 * @param eventType kind of transfer event to publish
 * @param payload   transfer event payload
 * @param traceId   originating request trace id when available
 */
public record ClaimedTransferOutboxEvent(
		UUID eventId,
		OutboxEventType eventType,
		TransferEventOutboxPayload payload,
		String traceId) {
}
