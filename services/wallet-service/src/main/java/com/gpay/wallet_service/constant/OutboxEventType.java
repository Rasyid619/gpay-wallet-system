package com.gpay.wallet_service.constant;

/* Kinds of durable outbox events published by wallet service. */
public enum OutboxEventType {
	TRANSFER_COMPLETED,
	TRANSFER_RECEIVED,
	TRANSFER_FAILED
}
