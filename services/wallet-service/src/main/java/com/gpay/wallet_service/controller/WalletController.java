package com.gpay.wallet_service.controller;

import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.service.WalletBalanceService;
import java.util.UUID;
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

	public WalletController(WalletBalanceService walletBalanceService) {
		this.walletBalanceService = walletBalanceService;
	}

	@GetMapping("/balance")
	public ResponseEntity<WalletBalanceResponse> getBalance(@AuthenticationPrincipal UUID userId) {
		return ResponseEntity.ok(walletBalanceService.getBalance(userId));
	}
}
