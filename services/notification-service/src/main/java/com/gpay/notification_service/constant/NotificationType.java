package com.gpay.notification_service.constant;

/* Kinds of transactional emails the notification service delivers. */
public enum NotificationType {

	TRANSFER_COMPLETED("email/transfer_completed", "Your transfer was sent"),
	TRANSFER_RECEIVED("email/transfer_received", "You received a transfer"),
	TRANSFER_FAILED("email/transfer_failed", "Your transfer could not be completed"),
	TOPUP_SUCCEEDED("email/topup_succeeded", "Your top-up was successful"),
	TOPUP_FAILED("email/topup_failed", "Your top-up could not be completed");

	private final String templateName;
	private final String subject;

	NotificationType(String templateName, String subject) {
		this.templateName = templateName;
		this.subject = subject;
	}

	public String getTemplateName() {
		return templateName;
	}

	public String getSubject() {
		return subject;
	}
}
