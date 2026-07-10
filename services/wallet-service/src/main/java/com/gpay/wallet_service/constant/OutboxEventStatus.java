package com.gpay.wallet_service.constant;

/* Delivery lifecycle of a durable wallet outbox event. */
public enum OutboxEventStatus {
	PENDING,
	PROCESSING,
	PROCESSED,
	FAILED
}
