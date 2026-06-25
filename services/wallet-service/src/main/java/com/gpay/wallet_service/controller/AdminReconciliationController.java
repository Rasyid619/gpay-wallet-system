package com.gpay.wallet_service.controller;

import com.gpay.wallet_service.dto.WalletReconciliationRunResponse;
import com.gpay.wallet_service.service.WalletReconciliationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/* Admin endpoints to trigger and inspect wallet reconciliation runs, gated on ROLE_ADMIN. */
@RestController
@RequestMapping("/admin/reconciliation")
@RequiredArgsConstructor
public class AdminReconciliationController {

	private final WalletReconciliationService reconciliationService;

	@PostMapping("/run")
	public ResponseEntity<WalletReconciliationRunResponse> runReconciliation() {
		return ResponseEntity.ok(reconciliationService.runReconciliation());
	}

	@GetMapping("/runs")
	public ResponseEntity<List<WalletReconciliationRunResponse>> getRecentRuns(
			@RequestParam(name = "limit", defaultValue = "20") int limit) {
		return ResponseEntity.ok(reconciliationService.findRecentRuns(limit));
	}
}
