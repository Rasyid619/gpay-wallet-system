package com.gpay.wallet_service.entity;

import com.gpay.wallet_service.constant.LedgerEntrySource;
import com.gpay.wallet_service.constant.LedgerEntryType;
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

/* Persisted immutable wallet mutation entry. */
@Getter
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

	@Id
	@Column(nullable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "wallet_id", nullable = false)
	private Wallet wallet;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "transfer_id")
	private Transfer transfer;

	@Column(name = "payment_transaction_id")
	private UUID paymentTransactionId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(name = "entry_type", nullable = false, columnDefinition = "ledger_entry_type")
	private LedgerEntryType entryType;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "ledger_entry_source")
	private LedgerEntrySource source;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "balance_after", nullable = false)
	private Long balanceAfter;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected LedgerEntry() {
	}

	/**
	 * Creates a wallet ledger entry with the balance after mutation.
	 *
	 * @param id                   unique ledger entry identifier
	 * @param wallet               wallet affected by this entry
	 * @param transfer             transfer source when applicable
	 * @param paymentTransactionId payment transaction source when applicable
	 * @param entryType            debit or credit direction
	 * @param source               producing workflow
	 * @param amount               mutation amount in whole IDR
	 * @param balanceAfter         wallet balance after applying this entry
	 * @param description          optional user-facing description
	 * @param createdAt            creation timestamp
	 */
	public static LedgerEntry create(
			UUID id,
			Wallet wallet,
			Transfer transfer,
			UUID paymentTransactionId,
			LedgerEntryType entryType,
			LedgerEntrySource source,
			Long amount,
			Long balanceAfter,
			String description,
			Instant createdAt) {
		LedgerEntry entry = new LedgerEntry();
		entry.id = id;
		entry.wallet = wallet;
		entry.transfer = transfer;
		entry.paymentTransactionId = paymentTransactionId;
		entry.entryType = entryType;
		entry.source = source;
		entry.amount = amount;
		entry.balanceAfter = balanceAfter;
		entry.description = description;
		entry.createdAt = createdAt;
		return entry;
	}
}
