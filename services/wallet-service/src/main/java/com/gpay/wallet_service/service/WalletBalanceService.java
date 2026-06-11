package com.gpay.wallet_service.service;

import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.entity.Wallet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Reads authenticated wallet balances. */
@Service
@RequiredArgsConstructor
public class WalletBalanceService {

	private final WalletProvisioner walletProvisioner;

	/**
	 * Returns the wallet balance for an authenticated user.
	 *
	 * <p>A missing wallet is provisioned on first access so a registered user can
	 * always read a balance even when registration-time provisioning did not run.
	 *
	 * @param userId authenticated user identifier
	 * @return narrow wallet balance response
	 */
	@Transactional
	public WalletBalanceResponse getBalance(UUID userId) {
		Wallet wallet = walletProvisioner.getOrProvision(userId);

		return new WalletBalanceResponse(wallet.getId(), wallet.getBalance());
	}
}
