package com.gpay.wallet_service.service;

import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/* Returns a user's wallet, creating a zero-balance wallet on first access when one is missing. */
@Service
@RequiredArgsConstructor
public class WalletProvisioner {

	private static final long INITIAL_BALANCE = 0L;

	private final WalletRepository walletRepository;

	/**
	 * Returns the wallet owned by the supplied user, provisioning a zero-balance
	 * wallet when none exists yet.
	 *
	 * <p>Provisioning is idempotent: repeated calls for the same user return the
	 * same wallet. This lets reads and transfers recover when registration-time
	 * provisioning did not complete (for example, while Wallet Service was down).
	 *
	 * @param userId authenticated user identifier
	 * @return the existing or newly provisioned wallet
	 */
	public Wallet getOrProvision(UUID userId) {
		return walletRepository.findByUserId(userId)
				.orElseGet(() -> createWallet(userId));
	}

	private Wallet createWallet(UUID userId) {
		Instant now = Instant.now();
		// Flush immediately so callers that follow up with a native FOR UPDATE
		// lock query (e.g. transfers) can see the newly provisioned wallet row.
		return walletRepository.saveAndFlush(Wallet.create(
				UUID.randomUUID(),
				userId,
				INITIAL_BALANCE,
				WalletStatus.ACTIVE,
				now,
				now));
	}
}
