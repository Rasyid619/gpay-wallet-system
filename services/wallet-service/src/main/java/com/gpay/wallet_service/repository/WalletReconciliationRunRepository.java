package com.gpay.wallet_service.repository;

import com.gpay.wallet_service.entity.WalletReconciliationRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/* Database access for wallet reconciliation run summaries. */
public interface WalletReconciliationRunRepository extends JpaRepository<WalletReconciliationRun, UUID> {

	/**
	 * Lists the most recent reconciliation runs, newest first.
	 *
	 * @param pageable page size and offset for the recent runs
	 * @return recent run summaries ordered by start time descending
	 */
	List<WalletReconciliationRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
