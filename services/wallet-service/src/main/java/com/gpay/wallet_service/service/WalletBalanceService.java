package com.gpay.wallet_service.service;

import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.exception.WalletNotFoundException;
import com.gpay.wallet_service.repository.WalletRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Reads authenticated wallet balances. */
@Service
public class WalletBalanceService {

	private final WalletRepository walletRepository;

	public WalletBalanceService(WalletRepository walletRepository) {
		this.walletRepository = walletRepository;
	}

	/**
	 * Returns the wallet balance for an authenticated user.
	 *
	 * @param userId authenticated user identifier
	 * @return narrow wallet balance response
	 */
	@Transactional(readOnly = true)
	public WalletBalanceResponse getBalance(UUID userId) {
		Wallet wallet = walletRepository.findByUserId(userId)
				.orElseThrow(() -> new WalletNotFoundException("Wallet was not found for authenticated user"));

		return new WalletBalanceResponse(wallet.getId(), wallet.getBalance());
	}
}
