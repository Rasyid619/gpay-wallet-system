package com.gpay.wallet_service.controller;

import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.dto.WalletMutationPageResponse;
import com.gpay.wallet_service.service.WalletBalanceService;
import com.gpay.wallet_service.service.WalletMutationService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Wallet endpoints for authenticated users. */
@RestController
@RequestMapping("/wallets")
public class WalletController {

	private final WalletBalanceService walletBalanceService;
	private final WalletMutationService walletMutationService;

	public WalletController(
			WalletBalanceService walletBalanceService,
			WalletMutationService walletMutationService) {
		this.walletBalanceService = walletBalanceService;
		this.walletMutationService = walletMutationService;
	}

	@GetMapping("/balance")
	public ResponseEntity<WalletBalanceResponse> getBalance(@AuthenticationPrincipal UUID userId) {
		return ResponseEntity.ok(walletBalanceService.getBalance(userId));
	}

	@GetMapping("/mutations")
	public ResponseEntity<WalletMutationPageResponse> getMutations(
			@AuthenticationPrincipal UUID userId,
			Pageable pageable) {
		return ResponseEntity.ok(walletMutationService.getMutations(userId, pageable));
	}
}
