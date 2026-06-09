package com.gpay.payment_service.entity;

import com.gpay.payment_service.constant.OutboxEventStatus;
import com.gpay.payment_service.constant.OutboxEventType;
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

/* Durable payment event pending delivery to another service. */
@Getter
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "outbox_event_type")
	private OutboxEventType eventType;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "outbox_event_status")
	private OutboxEventStatus status;

	@Column(name = "retry_count", nullable = false)
	private Integer retryCount;

	@Column(name = "next_retry_at")
	private Instant nextRetryAt;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected OutboxEvent() {
	}

	public static OutboxEvent createPending(
			UUID id,
			OutboxEventType eventType,
			UUID aggregateId,
			String payload,
			Instant now) {
		OutboxEvent event = new OutboxEvent();
		event.id = id;
		event.eventType = eventType;
		event.aggregateId = aggregateId;
		event.payload = payload;
		event.status = OutboxEventStatus.PENDING;
		event.retryCount = 0;
		event.nextRetryAt = now;
		event.createdAt = now;
		event.updatedAt = now;
		return event;
	}

	public void markProcessing(Instant now) {
		this.status = OutboxEventStatus.PROCESSING;
		this.updatedAt = now;
	}

	public void markProcessed(Instant now) {
		this.status = OutboxEventStatus.PROCESSED;
		this.lastError = null;
		this.nextRetryAt = null;
		this.updatedAt = now;
	}

	public void markFailedAttempt(String error, Instant nextRetryAt, Instant now) {
		this.status = OutboxEventStatus.PENDING;
		this.retryCount = this.retryCount + 1;
		this.lastError = error;
		this.nextRetryAt = nextRetryAt;
		this.updatedAt = now;
	}
}
