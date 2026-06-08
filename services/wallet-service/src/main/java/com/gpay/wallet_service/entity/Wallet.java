package com.gpay.wallet_service.entity;

import com.gpay.wallet_service.constant.WalletStatus;
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

/* Persisted wallet balance owned by one authenticated user. */
@Getter
@Entity
@Table(name = "wallets")
public class Wallet {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, unique = true)
	private UUID userId;

	@Column(nullable = false)
	private Long balance;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "wallet_status")
	private WalletStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Wallet() {
	}

	/**
	 * Creates a wallet balance row for a user.
	 *
	 * @param id        unique wallet identifier
	 * @param userId    owning auth-service user identifier
	 * @param balance   current balance in whole IDR
	 * @param status    wallet operational status
	 * @param createdAt creation timestamp
	 * @param updatedAt last update timestamp
	 */
	public static Wallet create(
			UUID id,
			UUID userId,
			Long balance,
			WalletStatus status,
			Instant createdAt,
			Instant updatedAt) {
		Wallet wallet = new Wallet();
		wallet.id = id;
		wallet.userId = userId;
		wallet.balance = balance;
		wallet.status = status;
		wallet.createdAt = createdAt;
		wallet.updatedAt = updatedAt;
		return wallet;
	}
}
