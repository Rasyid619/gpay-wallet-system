package com.gpay.wallet_service.controller;

import com.gpay.wallet_service.dto.InternalWalletProvisionRequest;
import com.gpay.wallet_service.dto.InternalWalletProvisionResponse;
import com.gpay.wallet_service.service.InternalWalletProvisionService;
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

	private final InternalWalletProvisionService internalWalletProvisionService;

	@PostMapping("/provision")
	public ResponseEntity<InternalWalletProvisionResponse> provision(
			@RequestHeader("X-Internal-Token") String internalToken,
			@Valid @RequestBody InternalWalletProvisionRequest request) {
		return ResponseEntity.ok(internalWalletProvisionService.provision(internalToken, request));
	}
}
