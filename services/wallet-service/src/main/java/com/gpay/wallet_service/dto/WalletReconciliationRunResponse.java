package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpay.wallet_service.entity.WalletReconciliationRun;
import java.time.Instant;
import java.util.UUID;

/**
 * Summary of a wallet reconciliation run.
 *
 * @param id                 unique run identifier
 * @param status             terminal run status
 * @param startedAt          run start timestamp
 * @param completedAt        run completion timestamp when finished
 * @param checkedWalletCount number of wallets compared
 * @param mismatchCount      number of detected mismatches
 * @param durationMs         elapsed run duration in milliseconds
 */
public record WalletReconciliationRunResponse(
		UUID id,
		String status,
		@JsonProperty("started_at") Instant startedAt,
		@JsonProperty("completed_at") Instant completedAt,
		@JsonProperty("checked_wallet_count") int checkedWalletCount,
		@JsonProperty("mismatch_count") int mismatchCount,
		@JsonProperty("duration_ms") Long durationMs) {

	/**
	 * Maps a reconciliation run entity to its response shape.
	 *
	 * @param run reconciliation run entity
	 * @return response carrying the run summary
	 */
	public static WalletReconciliationRunResponse from(WalletReconciliationRun run) {
		return new WalletReconciliationRunResponse(
				run.getId(),
				run.getStatus().name(),
				run.getStartedAt(),
				run.getCompletedAt(),
				run.getCheckedWalletCount(),
				run.getMismatchCount(),
				run.getDurationMs());
	}
}
