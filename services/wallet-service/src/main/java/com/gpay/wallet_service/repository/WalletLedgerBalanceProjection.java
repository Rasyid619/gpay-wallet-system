package com.gpay.wallet_service.repository;

import java.util.UUID;

/* Per-wallet comparison of stored balance against the ledger-derived balance. */
public interface WalletLedgerBalanceProjection {

	/** Wallet identifier. */
	UUID getWalletId();

	/** Balance stored on the wallet row in whole IDR. */
	Long getStoredBalance();

	/** Balance derived by summing signed ledger entries in whole IDR. */
	Long getLedgerBalance();
}
