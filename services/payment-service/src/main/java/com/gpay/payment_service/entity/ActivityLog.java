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
}
