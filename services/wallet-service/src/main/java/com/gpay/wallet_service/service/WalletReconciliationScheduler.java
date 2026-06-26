package com.gpay.wallet_service.service;

import com.gpay.wallet_service.config.WalletReconciliationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/* Triggers the wallet reconciliation job on the configured schedule when enabled. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletReconciliationScheduler {

	private final WalletReconciliationProperties properties;
	private final WalletReconciliationService reconciliationService;

	@Scheduled(cron = "${wallet.reconciliation.cron}")
	public void runScheduledReconciliation() {
		if (!properties.enabled()) {
			return;
		}

		reconciliationService.runReconciliation();
	}
}
