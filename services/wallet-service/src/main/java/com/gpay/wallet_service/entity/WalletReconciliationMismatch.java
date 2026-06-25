package com.gpay.wallet_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/* Persisted record of one wallet whose stored balance differs from its ledger balance. */
@Getter
@Entity
@Table(name = "wallet_reconciliation_mismatches")
public class WalletReconciliationMismatch {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "wallet_id", nullable = false)
	private UUID walletId;

	@Column(name = "stored_balance", nullable = false)
	private Long storedBalance;

	@Column(name = "ledger_balance", nullable = false)
	private Long ledgerBalance;

	@Column(nullable = false)
	private Long difference;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected WalletReconciliationMismatch() {
	}

	/**
	 * Creates a reconciliation mismatch row where {@code difference} is stored minus ledger balance.
	 *
	 * @param id            unique mismatch identifier
	 * @param runId         owning reconciliation run identifier
	 * @param walletId      wallet whose balances disagree
	 * @param storedBalance balance stored on the wallet row in whole IDR
	 * @param ledgerBalance balance derived from ledger entries in whole IDR
	 * @param difference    stored balance minus ledger balance in whole IDR
	 * @param createdAt     creation timestamp
	 * @return new mismatch record
	 */
	public static WalletReconciliationMismatch create(
			UUID id,
			UUID runId,
			UUID walletId,
			Long storedBalance,
			Long ledgerBalance,
			Long difference,
			Instant createdAt) {
		WalletReconciliationMismatch mismatch = new WalletReconciliationMismatch();
		mismatch.id = id;
		mismatch.runId = runId;
		mismatch.walletId = walletId;
		mismatch.storedBalance = storedBalance;
		mismatch.ledgerBalance = ledgerBalance;
		mismatch.difference = difference;
		mismatch.createdAt = createdAt;
		return mismatch;
	}
}
