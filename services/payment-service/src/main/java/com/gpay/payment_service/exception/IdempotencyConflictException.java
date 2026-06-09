package com.gpay.payment_service.exception;

/* Signals reuse of an idempotency key with different request data. */
public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException(String message) {
		super(message);
	}
}
