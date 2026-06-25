package com.gpay.wallet_service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gpay.wallet_service.constant.LedgerEntrySource;
import com.gpay.wallet_service.constant.LedgerEntryType;
import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.dto.WalletReconciliationRunResponse;
import com.gpay.wallet_service.entity.LedgerEntry;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.entity.WalletReconciliationMismatch;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import com.gpay.wallet_service.repository.WalletReconciliationMismatchRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import com.gpay.wallet_service.service.WalletReconciliationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * Integration tests for wallet ledger reconciliation, covering a wallet whose ledger matches its
 * stored balance and a wallet whose ledger disagrees, against the real Flyway-migrated schema.
 */
class WalletReconciliationIntegrationTest extends AbstractIntegrationTest {

	private final WalletRepository walletRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final WalletReconciliationMismatchRepository mismatchRepository;
	private final WalletReconciliationService reconciliationService;

	@Autowired
	WalletReconciliationIntegrationTest(
			WalletRepository walletRepository,
			LedgerEntryRepository ledgerEntryRepository,
			WalletReconciliationMismatchRepository mismatchRepository,
			WalletReconciliationService reconciliationService) {
		this.walletRepository = walletRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.mismatchRepository = mismatchRepository;
		this.reconciliationService = reconciliationService;
	}

	private Wallet seedWallet(Long balance) {
		Instant now = Instant.now();
		return walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				UUID.randomUUID(),
				balance,
				WalletStatus.ACTIVE,
				now,
				now));
	}

	private void seedTopUpEntry(Wallet wallet, LedgerEntryType type, Long amount, Long balanceAfter) {
		ledgerEntryRepository.save(LedgerEntry.create(
				UUID.randomUUID(),
				wallet,
				null,
				UUID.randomUUID(),
				type,
				LedgerEntrySource.TOP_UP,
				amount,
				balanceAfter,
				"seed",
				Instant.now()));
	}

	@Test
	void recordsNoMismatchWhenLedgerMatchesStoredBalance() {
		Wallet wallet = seedWallet(10_000L);
		seedTopUpEntry(wallet, LedgerEntryType.CREDIT, 15_000L, 15_000L);
		seedTopUpEntry(wallet, LedgerEntryType.DEBIT, 5_000L, 10_000L);

		WalletReconciliationRunResponse run = reconciliationService.runReconciliation();

		assertThat(run.checkedWalletCount()).isEqualTo(1);
		assertThat(run.mismatchCount()).isZero();
		assertThat(mismatchRepository.findByRunId(run.id())).isEmpty();
	}

	@Test
	void recordsMismatchWhenLedgerDisagreesWithStoredBalance() {
		Wallet wallet = seedWallet(10_000L);
		seedTopUpEntry(wallet, LedgerEntryType.CREDIT, 5_000L, 5_000L);

		WalletReconciliationRunResponse run = reconciliationService.runReconciliation();

		assertThat(run.checkedWalletCount()).isEqualTo(1);
		assertThat(run.mismatchCount()).isEqualTo(1);

		List<WalletReconciliationMismatch> mismatches = mismatchRepository.findByRunId(run.id());
		assertThat(mismatches).hasSize(1);
		WalletReconciliationMismatch mismatch = mismatches.get(0);
		assertThat(mismatch.getWalletId()).isEqualTo(wallet.getId());
		assertThat(mismatch.getStoredBalance()).isEqualTo(10_000L);
		assertThat(mismatch.getLedgerBalance()).isEqualTo(5_000L);
		assertThat(mismatch.getDifference()).isEqualTo(5_000L);
	}
}
