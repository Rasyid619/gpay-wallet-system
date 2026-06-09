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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
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

	/**
	 * Creates a pending payment top-up transaction.
	 *
	 * @param id             unique payment transaction identifier
	 * @param userId         authenticated user identifier
	 * @param walletId       wallet intended to receive the top-up
	 * @param amount         top-up amount in whole IDR
	 * @param idempotencyKey client-provided idempotency key
	 * @param traceId        request trace identifier when supplied
	 * @param now            creation timestamp
	 * @return top-up transaction entity
	 */
	public static TopupTransaction createPending(
			UUID id,
			UUID userId,
			UUID walletId,
			Long amount,
			String idempotencyKey,
			String traceId,
			Instant now) {
		TopupTransaction transaction = new TopupTransaction();
		transaction.id = id;
		transaction.userId = userId;
		transaction.walletId = walletId;
		transaction.amount = amount;
		transaction.status = PaymentStatus.PENDING;
		transaction.idempotencyKey = idempotencyKey;
		transaction.traceId = traceId;
		transaction.createdAt = now;
		transaction.updatedAt = now;
		return transaction;
	}

	public void markSuccess(String gatewayReference, Instant now) {
		this.status = PaymentStatus.SUCCESS;
		this.gatewayReference = gatewayReference;
		this.failureReason = null;
		this.updatedAt = now;
	}

	public void markFailed(String gatewayReference, String failureReason, Instant now) {
		this.status = PaymentStatus.FAILED;
		this.gatewayReference = gatewayReference;
		this.failureReason = failureReason;
		this.updatedAt = now;
	}
}
