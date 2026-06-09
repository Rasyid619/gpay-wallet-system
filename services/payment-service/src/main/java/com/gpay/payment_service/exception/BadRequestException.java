package com.gpay.payment_service.exception;

/* Signals invalid payment request input. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
