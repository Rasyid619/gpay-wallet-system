package com.gpay.wallet_service.service;

import com.gpay.wallet_service.dto.InternalWalletProvisionRequest;
import com.gpay.wallet_service.dto.InternalWalletProvisionResponse;
import com.gpay.wallet_service.entity.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Provisions zero-balance wallets for users created by Auth Service. */
@Service
@RequiredArgsConstructor
public class InternalWalletProvisionService {

	private final InternalWalletAuthenticationService internalWalletAuthenticationService;
	private final WalletProvisioner walletProvisioner;

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

		Wallet wallet = walletProvisioner.getOrProvision(request.userId());
		return response(wallet);
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
