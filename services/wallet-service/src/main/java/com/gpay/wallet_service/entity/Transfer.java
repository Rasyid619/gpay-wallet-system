package com.gpay.wallet_service.entity;

import com.gpay.wallet_service.constant.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/* Persisted wallet-to-wallet transfer attempt. */
@Getter
@Entity
@Table(name = "transfers")
public class Transfer {

	@Id
	@Column(nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_wallet_id", nullable = false)
	private Wallet senderWallet;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "receiver_wallet_id", nullable = false)
	private Wallet receiverWallet;

	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "transfer_status")
	private TransferStatus status;

	@Column(name = "failure_reason", columnDefinition = "text")
	private String failureReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Transfer() {
	}

	/**
	 * Creates a transfer record with its resulting status.
	 *
	 * @param id             unique transfer identifier
	 * @param senderWallet   debited wallet
	 * @param receiverWallet credited wallet
	 * @param amount         transfer amount in whole IDR
	 * @param status         final transfer status
	 * @param failureReason  optional rejection reason
	 * @param createdAt      creation timestamp
	 */
	public static Transfer create(
			UUID id,
			Wallet senderWallet,
			Wallet receiverWallet,
			Long amount,
			TransferStatus status,
			String failureReason,
			Instant createdAt) {
		Transfer transfer = new Transfer();
		transfer.id = id;
		transfer.senderWallet = senderWallet;
		transfer.receiverWallet = receiverWallet;
		transfer.amount = amount;
		transfer.status = status;
		transfer.failureReason = failureReason;
		transfer.createdAt = createdAt;
		return transfer;
	}
}
