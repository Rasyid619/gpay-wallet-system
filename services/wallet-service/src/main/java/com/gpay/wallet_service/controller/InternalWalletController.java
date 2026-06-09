package com.gpay.wallet_service.controller;

import com.gpay.wallet_service.dto.IdempotentResponse;
import com.gpay.wallet_service.dto.InternalWalletCreditRequest;
import com.gpay.wallet_service.service.InternalWalletCreditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Internal wallet endpoints for service-to-service workflows. */
@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
public class InternalWalletController {

	private final InternalWalletCreditService internalWalletCreditService;

	@PostMapping("/credit")
	public ResponseEntity<Object> credit(
			@RequestHeader("X-Internal-Token") String internalToken,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(value = "X-Trace-Id", required = false) String traceId,
			@Valid @RequestBody InternalWalletCreditRequest request) {
		IdempotentResponse response = internalWalletCreditService.credit(
				internalToken,
				idempotencyKey,
				request,
				traceId);
		return ResponseEntity.status(response.status()).body(response.body());
	}
}
