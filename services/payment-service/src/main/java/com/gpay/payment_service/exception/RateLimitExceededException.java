package com.gpay.payment_service.exception;

/* Signals that a payment top-up request exceeded the configured rate limit. */
public class RateLimitExceededException extends RuntimeException {

	public RateLimitExceededException(String message) {
		super(message);
	}
}
