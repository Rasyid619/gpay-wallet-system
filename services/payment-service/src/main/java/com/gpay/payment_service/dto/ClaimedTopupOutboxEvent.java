package com.gpay.payment_service.dto;

import com.gpay.payment_service.constant.OutboxEventType;
import java.util.UUID;

/**
 * Top-up event claimed for publishing, with its deserialized payload.
 *
 * @param eventId   outbox event identifier
 * @param eventType succeeded or failed top-up event kind
 * @param payload   top-up event payload
 * @param traceId   originating request trace id when available
 */
public record ClaimedTopupOutboxEvent(
		UUID eventId,
		OutboxEventType eventType,
		TopupEventOutboxPayload payload,
		String traceId) {
}
