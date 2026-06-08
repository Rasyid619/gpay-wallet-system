package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Wallet balance response expressed as whole IDR.
 *
 * @param walletId unique wallet identifier
 * @param balance  current balance in whole IDR
 */
public record WalletBalanceResponse(
		@JsonProperty("wallet_id") UUID walletId,
		Long balance) {
}
