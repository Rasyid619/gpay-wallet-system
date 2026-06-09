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

/* Durable response cache for mutating payment idempotency keys. */
@Getter
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "idempotency_key", nullable = false, columnDefinition = "text")
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, columnDefinition = "text")
	private String requestHash;

	@Column(name = "response_status", nullable = false)
	private Integer responseStatus;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "response_body", nullable = false, columnDefinition = "jsonb")
	private String responseBody;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected IdempotencyKey() {
	}
}
