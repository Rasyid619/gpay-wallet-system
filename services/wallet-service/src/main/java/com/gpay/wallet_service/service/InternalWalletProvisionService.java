package com.gpay.wallet_service.service;

import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.dto.InternalWalletProvisionRequest;
import com.gpay.wallet_service.dto.InternalWalletProvisionResponse;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Provisions zero-balance wallets for users created by Auth Service. */
@Service
@RequiredArgsConstructor
public class InternalWalletProvisionService {

	private static final long INITIAL_BALANCE = 0L;

	private final InternalWalletAuthenticationService internalWalletAuthenticationService;
	private final WalletRepository walletRepository;

	/**
	 * Creates or returns the wallet owned by the supplied user id.
	 *
	 * @param providedInternalToken internal service token from the request header
	 * @param request               wallet provisioning request
	 * @return provisioned or existing wallet details
	 */
	@Transactional
	public InternalWalletProvisionResponse provision(
			String providedInternalToken,
			InternalWalletProvisionRequest request) {
		internalWalletAuthenticationService.validate(providedInternalToken);

		Wallet wallet = walletRepository.findByUserId(request.userId())
				.orElseGet(() -> createWallet(request.userId()));
		return response(wallet);
	}

	private Wallet createWallet(UUID userId) {
		Instant now = Instant.now();
		return walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				userId,
				INITIAL_BALANCE,
				WalletStatus.ACTIVE,
				now,
				now));
	}

	private InternalWalletProvisionResponse response(Wallet wallet) {
		return new InternalWalletProvisionResponse(
				wallet.getId(),
				wallet.getUserId(),
				wallet.getBalance(),
				wallet.getStatus().name(),
				wallet.getCreatedAt());
	}
}
