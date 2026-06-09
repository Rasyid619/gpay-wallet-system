package com.gpay.payment_service.controller;

import com.gpay.payment_service.dto.IdempotentResponse;
import com.gpay.payment_service.dto.TopUpRequest;
import com.gpay.payment_service.service.PaymentTopUpService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Payment endpoints for authenticated top-up workflows. */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentTopUpService paymentTopUpService;

	@PostMapping("/top-up")
	public ResponseEntity<Object> topUp(
			@AuthenticationPrincipal UUID userId,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestHeader(value = "X-Trace-Id", required = false) String traceId,
			@Valid @RequestBody TopUpRequest request) {
		IdempotentResponse response = paymentTopUpService.topUp(userId, idempotencyKey, request, traceId);
		return ResponseEntity.status(response.status()).body(response.body());
	}
}
