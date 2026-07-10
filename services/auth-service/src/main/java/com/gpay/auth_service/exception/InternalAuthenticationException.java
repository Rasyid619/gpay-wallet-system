package com.gpay.auth_service.exception;

/* Raised when an internal service-to-service token is missing or invalid. */
public class InternalAuthenticationException extends RuntimeException {

	public InternalAuthenticationException(String message) {
		super(message);
	}
}
