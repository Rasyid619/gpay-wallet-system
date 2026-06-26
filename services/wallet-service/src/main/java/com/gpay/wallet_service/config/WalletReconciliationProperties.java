package com.gpay.wallet_service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Scheduling configuration for the wallet ledger reconciliation job.
 *
 * @param enabled whether the scheduled reconciliation trigger runs
 * @param cron    cron expression controlling the scheduled trigger cadence
 */
@Validated
@ConfigurationProperties(prefix = "wallet.reconciliation")
public record WalletReconciliationProperties(
		@NotNull Boolean enabled,
		@NotBlank String cron) {
}
