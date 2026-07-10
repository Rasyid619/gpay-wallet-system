package com.gpay.notification_service.exception;

/**
 * Raised for notification failures that cannot succeed on retry (malformed
 * event, unknown user, invalid recipient address). The Kafka error handler
 * routes these straight to the dead-letter topic.
 */
public class NonRetryableNotificationException extends RuntimeException {

	public NonRetryableNotificationException(String message) {
		super(message);
	}

	public NonRetryableNotificationException(String message, Throwable cause) {
		super(message, cause);
	}
}
