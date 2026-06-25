package com.gpay.wallet_service.entity;

import com.gpay.wallet_service.constant.ReconciliationRunStatus;
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

/* Persisted summary of a single wallet ledger reconciliation run. */
@Getter
@Entity
@Table(name = "wallet_reconciliation_runs")
public class WalletReconciliationRun {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "reconciliation_run_status")
	private ReconciliationRunStatus status;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "checked_wallet_count", nullable = false)
	private int checkedWalletCount;

	@Column(name = "mismatch_count", nullable = false)
	private int mismatchCount;

	@Column(name = "duration_ms")
	private Long durationMs;

	protected WalletReconciliationRun() {
	}

	/**
	 * Starts a reconciliation run in {@link ReconciliationRunStatus#STARTED}.
	 *
	 * @param id        unique run identifier
	 * @param startedAt run start timestamp
	 * @return new run summary in STARTED status
	 */
	public static WalletReconciliationRun start(UUID id, Instant startedAt) {
		WalletReconciliationRun run = new WalletReconciliationRun();
		run.id = id;
		run.status = ReconciliationRunStatus.STARTED;
		run.startedAt = startedAt;
		run.checkedWalletCount = 0;
		run.mismatchCount = 0;
		return run;
	}

	/**
	 * Finalizes this run with its outcome and timing.
	 *
	 * @param status             terminal run status
	 * @param checkedWalletCount number of wallets compared
	 * @param mismatchCount      number of detected mismatches
	 * @param completedAt        run completion timestamp
	 * @param durationMs         elapsed run duration in milliseconds
	 */
	public void complete(
			ReconciliationRunStatus status,
			int checkedWalletCount,
			int mismatchCount,
			Instant completedAt,
			Long durationMs) {
		this.status = status;
		this.checkedWalletCount = checkedWalletCount;
		this.mismatchCount = mismatchCount;
		this.completedAt = completedAt;
		this.durationMs = durationMs;
	}
}
