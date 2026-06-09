package com.gpay.payment_service.exception;

public class InvalidWebhookSignatureException extends RuntimeException {

	public InvalidWebhookSignatureException(String message) {
		super(message);
	}
}
