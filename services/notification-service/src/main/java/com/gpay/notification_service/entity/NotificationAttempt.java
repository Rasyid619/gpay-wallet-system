package com.gpay.notification_service.entity;

import com.gpay.notification_service.constant.NotificationStatus;
import com.gpay.notification_service.constant.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/* Persisted delivery attempt for one notification event. */
@Getter
@Entity
@Table(name = "notification_attempts")
public class NotificationAttempt {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "event_id", nullable = false, unique = true)
	private UUID eventId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "notification_type", nullable = false, columnDefinition = "notification_type")
	private NotificationType notificationType;

	@Column(name = "recipient_email", nullable = false, columnDefinition = "text")
	private String recipientEmail;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "notification_status")
	private NotificationStatus status;

	@Column(name = "retry_count", nullable = false)
	private Integer retryCount;

	@Column(name = "trace_id", columnDefinition = "text")
	private String traceId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	protected NotificationAttempt() {
	}

	/**
	 * Creates a pending notification attempt awaiting its first delivery.
	 *
	 * @param id               unique attempt identifier
	 * @param eventId          unique event identifier used for idempotent dedup
	 * @param notificationType kind of transactional email
	 * @param recipientEmail   resolved recipient email address
	 * @param traceId          propagated trace identifier when supplied
	 * @param now              creation timestamp
	 */
	public static NotificationAttempt createPending(
			UUID id,
			UUID eventId,
			NotificationType notificationType,
			String recipientEmail,
			String traceId,
			Instant now) {
		NotificationAttempt attempt = new NotificationAttempt();
		attempt.id = id;
		attempt.eventId = eventId;
		attempt.notificationType = notificationType;
		attempt.recipientEmail = recipientEmail;
		attempt.status = NotificationStatus.PENDING;
		attempt.retryCount = 0;
		attempt.traceId = traceId;
		attempt.createdAt = now;
		attempt.updatedAt = now;
		return attempt;
	}

	public void markSent(Instant now) {
		this.status = NotificationStatus.SENT;
		this.sentAt = now;
		this.updatedAt = now;
	}

	/**
	 * Counts a failed delivery attempt while keeping the notification retryable.
	 *
	 * @param now attempt timestamp
	 */
	public void markFailedAttempt(Instant now) {
		this.status = NotificationStatus.PENDING;
		this.retryCount = this.retryCount + 1;
		this.updatedAt = now;
	}

	/**
	 * Moves the attempt to the terminal failed state so it is no longer retried.
	 *
	 * @param now failure timestamp
	 */
	public void markFailed(Instant now) {
		this.status = NotificationStatus.FAILED;
		this.retryCount = this.retryCount + 1;
		this.updatedAt = now;
	}
}
