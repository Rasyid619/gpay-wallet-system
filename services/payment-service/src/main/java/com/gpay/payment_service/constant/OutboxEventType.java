package com.gpay.payment_service.constant;

/* Durable payment outbox event types. */
public enum OutboxEventType {
	CREDIT_WALLET_REQUESTED,
	TOPUP_SUCCEEDED,
	TOPUP_FAILED
}
