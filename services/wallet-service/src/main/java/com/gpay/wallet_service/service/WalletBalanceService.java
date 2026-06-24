package com.gpay.wallet_service.service;

import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.exception.WalletNotFoundException;
import com.gpay.wallet_service.repository.WalletRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Reads authenticated wallet balances. */
@Service
@RequiredArgsConstructor
public class WalletBalanceService {

	private final WalletProvisioner walletProvisioner;
	private final WalletRepository walletRepository;

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

	/**
	 * Returns any wallet balance by wallet id for administrative lookups.
	 *
	 * <p>Ownership-agnostic: this reads the wallet directly by its identifier rather than by
	 * owner, so an administrator can inspect any user's wallet.
	 *
	 * @param walletId wallet identifier
	 * @return narrow wallet balance response
	 * @throws WalletNotFoundException when no wallet exists for the identifier
	 */
	@Transactional(readOnly = true)
	public WalletBalanceResponse getWalletById(UUID walletId) {
		Wallet wallet = walletRepository.findById(walletId)
				.orElseThrow(() -> new WalletNotFoundException("Wallet was not found"));

		return new WalletBalanceResponse(wallet.getId(), wallet.getBalance());
	}
}
