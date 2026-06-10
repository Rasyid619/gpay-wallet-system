package com.gpay.wallet_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/* User-facing wallet activity log entry. */
@Getter
@Entity
@Table(name = "activity_logs")
public class ActivityLog {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "wallet_id")
	private Wallet wallet;

	@Column(nullable = false, columnDefinition = "text")
	private String action;

	@Column(nullable = false, columnDefinition = "text")
	private String status;

	@Column(name = "trace_id", columnDefinition = "text")
	private String traceId;

	@Column(name = "service_name", nullable = false, columnDefinition = "text")
	private String serviceName;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String metadata;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ActivityLog() {
	}

	/**
	 * Creates a user-facing activity log record.
	 *
	 * @param id          unique log identifier
	 * @param userId      user associated with the activity when available
	 * @param wallet      wallet associated with the activity when available
	 * @param action      activity action name
	 * @param status      activity result status
	 * @param traceId     propagated trace identifier
	 * @param serviceName service that created the log
	 * @param metadata    optional JSON metadata
	 * @param createdAt   creation timestamp
	 */
	public static ActivityLog create(
			UUID id,
			UUID userId,
			Wallet wallet,
			String action,
			String status,
			String traceId,
			String serviceName,
			String metadata,
			Instant createdAt) {
		ActivityLog log = new ActivityLog();
		log.id = id;
		log.userId = userId;
		log.wallet = wallet;
		log.action = action;
		log.status = status;
		log.traceId = traceId;
		log.serviceName = serviceName;
		log.metadata = metadata;
		log.createdAt = createdAt;
		return log;
	}
}
