package com.gpay.payment_service.exception;

/* Signals that payment rate limit verification cannot be performed safely. */
public class RateLimitUnavailableException extends RuntimeException {

	public RateLimitUnavailableException(String message) {
		super(message);
	}
}
