package com.gpay.notification_service.exception;

/**
 * Raised for transient notification failures (SMTP connectivity, auth-service
 * lookup unavailability) that may succeed on a later delivery attempt. The
 * Kafka error handler retries these before dead-lettering.
 */
public class EmailDeliveryException extends RuntimeException {

	public EmailDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}
}
