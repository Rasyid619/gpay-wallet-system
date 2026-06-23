package com.gpay.wallet_service.controller;

import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.service.WalletBalanceService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Admin endpoint for looking up any wallet by id, gated on ROLE_ADMIN. */
@RestController
@RequestMapping("/admin/wallets")
@RequiredArgsConstructor
public class AdminWalletController {

	private final WalletBalanceService walletBalanceService;

	@GetMapping("/{id}")
	public ResponseEntity<WalletBalanceResponse> getWallet(@PathVariable UUID id) {
		return ResponseEntity.ok(walletBalanceService.getWalletById(id));
	}
}
