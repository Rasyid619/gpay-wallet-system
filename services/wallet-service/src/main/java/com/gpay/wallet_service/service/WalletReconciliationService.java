package com.gpay.wallet_service.service;

import com.gpay.wallet_service.constant.ReconciliationRunStatus;
import com.gpay.wallet_service.dto.WalletReconciliationRunResponse;
import com.gpay.wallet_service.entity.WalletReconciliationMismatch;
import com.gpay.wallet_service.entity.WalletReconciliationRun;
import com.gpay.wallet_service.repository.WalletLedgerBalanceProjection;
import com.gpay.wallet_service.repository.WalletReconciliationMismatchRepository;
import com.gpay.wallet_service.repository.WalletReconciliationRunRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles every wallet's stored balance against its ledger-derived balance.
 *
 * <p>The job is read-only over wallets and ledger entries and never mutates wallet
 * balances; it only writes run summaries and detected mismatches to its own tables.
 * Difference is defined as stored balance minus ledger balance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletReconciliationService {

	private static final String ACTION_RECONCILE = "WALLET_RECONCILIATION";
	private static final String SERVICE_NAME = "wallet-service";

	private final WalletRepository walletRepository;
	private final WalletReconciliationRunRepository runRepository;
	private final WalletReconciliationMismatchRepository mismatchRepository;

	/**
	 * Runs a full reconciliation pass over all wallets and records the outcome.
	 *
	 * @return summary of the completed run
	 */
	@Transactional
	public WalletReconciliationRunResponse runReconciliation() {
		Instant startedAt = Instant.now();
		WalletReconciliationRun run = runRepository.save(
				WalletReconciliationRun.start(UUID.randomUUID(), startedAt));

		List<WalletLedgerBalanceProjection> balances = walletRepository.findWalletLedgerBalances();
		List<WalletReconciliationMismatch> mismatches = detectMismatches(run.getId(), balances, startedAt);
		mismatchRepository.saveAll(mismatches);

		Instant completedAt = Instant.now();
		long durationMs = completedAt.toEpochMilli() - startedAt.toEpochMilli();
		run.complete(
				ReconciliationRunStatus.COMPLETED,
				balances.size(),
				mismatches.size(),
				completedAt,
				durationMs);
		runRepository.save(run);

		logCompletion(run);
		return WalletReconciliationRunResponse.from(run);
	}

	/**
	 * Lists the most recent reconciliation runs, newest first.
	 *
	 * @param limit maximum number of runs to return
	 * @return recent run summaries
	 */
	@Transactional(readOnly = true)
	public List<WalletReconciliationRunResponse> findRecentRuns(int limit) {
		return runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
				.stream()
				.map(WalletReconciliationRunResponse::from)
				.toList();
	}

	private List<WalletReconciliationMismatch> detectMismatches(
			UUID runId,
			List<WalletLedgerBalanceProjection> balances,
			Instant createdAt) {
		List<WalletReconciliationMismatch> mismatches = new ArrayList<>();
		for (WalletLedgerBalanceProjection balance : balances) {
			long difference = balance.getStoredBalance() - balance.getLedgerBalance();
			if (difference == 0L) {
				continue;
			}

			mismatches.add(WalletReconciliationMismatch.create(
					UUID.randomUUID(),
					runId,
					balance.getWalletId(),
					balance.getStoredBalance(),
					balance.getLedgerBalance(),
					difference,
					createdAt));
		}
		return mismatches;
	}

	private void logCompletion(WalletReconciliationRun run) {
		log.info(
				"reconciliation completed serviceName={} action={} status={} durationMs={} "
						+ "checkedWalletCount={} mismatchCount={}",
				SERVICE_NAME,
				ACTION_RECONCILE,
				run.getStatus(),
				run.getDurationMs(),
				run.getCheckedWalletCount(),
				run.getMismatchCount());
	}
}
