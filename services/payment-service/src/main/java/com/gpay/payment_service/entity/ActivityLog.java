package com.gpay.payment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/* User-facing payment activity log entry. */
@Getter
@Entity
@Table(name = "activity_logs")
public class ActivityLog {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "transaction_id")
	private UUID transactionId;

	@Column(nullable = false, columnDefinition = "text")
	private String action;

	@Column(nullable = false, columnDefinition = "text")
	private String status;

	@Column(name = "trace_id", columnDefinition = "text")
	private String traceId;

	@Column(name = "service_name", nullable = false, columnDefinition = "text")
	private String serviceName;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "request_payload", columnDefinition = "jsonb")
	private String requestPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "response_payload", columnDefinition = "jsonb")
	private String responsePayload;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ActivityLog() {
	}

	/**
	 * Creates a user-facing payment activity record.
	 *
	 * @param id              unique log identifier
	 * @param traceId         propagated trace identifier
	 * @param userId          user associated with the activity
	 * @param transactionId   payment transaction associated with the activity
	 * @param serviceName     service that created the log
	 * @param action          lifecycle action name
	 * @param status          lifecycle result status
	 * @param requestPayload  optional JSON request payload
	 * @param responsePayload optional JSON response payload
	 * @param durationMs      operation duration in milliseconds when applicable
	 * @param createdAt       creation timestamp
	 * @return payment activity log entity
	 */
	public static ActivityLog create(
			UUID id,
			String traceId,
			UUID userId,
			UUID transactionId,
			String serviceName,
			String action,
			String status,
			String requestPayload,
			String responsePayload,
			Long durationMs,
			Instant createdAt) {
		ActivityLog log = new ActivityLog();
		log.id = id;
		log.traceId = traceId;
		log.userId = userId;
		log.transactionId = transactionId;
		log.serviceName = serviceName;
		log.action = action;
		log.status = status;
		log.requestPayload = requestPayload;
		log.responsePayload = responsePayload;
		log.durationMs = durationMs;
		log.createdAt = createdAt;
		return log;
	}
}
