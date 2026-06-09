package com.gpay.payment_service.entity;

import com.gpay.payment_service.constant.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/* Persisted payment top-up transaction owned by payment service. */
@Getter
@Entity
@Table(name = "topup_transactions")
public class TopupTransaction {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "wallet_id", nullable = false)
	private UUID walletId;

	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "payment_status")
	private PaymentStatus status;

	@Column(name = "gateway_reference", columnDefinition = "text")
	private String gatewayReference;

	@Column(name = "failure_reason", columnDefinition = "text")
	private String failureReason;

	@Column(name = "idempotency_key", nullable = false, columnDefinition = "text")
	private String idempotencyKey;

	@Column(name = "trace_id", columnDefinition = "text")
	private String traceId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected TopupTransaction() {
	}
}
