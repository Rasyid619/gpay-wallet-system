package com.gpay.wallet_service.repository;

import com.gpay.wallet_service.entity.WalletReconciliationMismatch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/* Database access for wallet reconciliation mismatch records. */
public interface WalletReconciliationMismatchRepository
		extends JpaRepository<WalletReconciliationMismatch, UUID> {

	/**
	 * Lists mismatches recorded for a reconciliation run.
	 *
	 * @param runId reconciliation run identifier
	 * @return mismatch records belonging to the run
	 */
	List<WalletReconciliationMismatch> findByRunId(UUID runId);
}
