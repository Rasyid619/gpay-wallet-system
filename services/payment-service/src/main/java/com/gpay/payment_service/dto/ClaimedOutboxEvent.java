package com.gpay.payment_service.dto;

import java.util.UUID;

public record ClaimedOutboxEvent(
		UUID eventId,
		WalletCreditOutboxPayload payload,
		String traceId) {
}
