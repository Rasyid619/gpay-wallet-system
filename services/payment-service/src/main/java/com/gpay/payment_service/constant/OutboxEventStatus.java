package com.gpay.payment_service.constant;

/* Processing state for durable payment outbox events. */
public enum OutboxEventStatus {
	PENDING,
	PROCESSING,
	PROCESSED,
	FAILED
}
