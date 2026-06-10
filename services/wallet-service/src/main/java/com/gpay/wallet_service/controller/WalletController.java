package com.gpay.wallet_service.controller;

import com.gpay.wallet_service.config.TraceIdContext;
import com.gpay.wallet_service.dto.IdempotentResponse;
import com.gpay.wallet_service.dto.TransferRequest;
import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.dto.WalletMutationPageResponse;
import com.gpay.wallet_service.service.WalletBalanceService;
import com.gpay.wallet_service.service.WalletMutationService;
import com.gpay.wallet_service.service.WalletTransferService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Wallet endpoints for authenticated users. */
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

	private final WalletBalanceService walletBalanceService;
	private final WalletMutationService walletMutationService;
	private final WalletTransferService walletTransferService;

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

	@PostMapping("/transfer")
	public ResponseEntity<Object> transfer(
			@AuthenticationPrincipal UUID userId,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody TransferRequest request) {
		IdempotentResponse response = walletTransferService.transfer(
				userId,
				idempotencyKey,
				request,
				TraceIdContext.getTraceId());
		return ResponseEntity.status(response.status()).body(response.body());
	}
}
