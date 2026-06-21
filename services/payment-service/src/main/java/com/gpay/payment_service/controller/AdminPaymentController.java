package com.gpay.payment_service.controller;

import com.gpay.common.tracing.TraceIdContext;
import com.gpay.payment_service.dto.TopUpResponse;
import com.gpay.payment_service.service.PaymentTopUpService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Admin and support endpoints for looking up any payment transaction by id. */
@RestController
@RequestMapping("/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

	private final PaymentTopUpService paymentTopUpService;

	@GetMapping("/{id}")
	public ResponseEntity<TopUpResponse> getPayment(
			@AuthenticationPrincipal UUID adminUserId,
			@PathVariable UUID id) {
		return ResponseEntity.ok(paymentTopUpService.fetchPayment(adminUserId, id, TraceIdContext.getTraceId()));
	}
}
