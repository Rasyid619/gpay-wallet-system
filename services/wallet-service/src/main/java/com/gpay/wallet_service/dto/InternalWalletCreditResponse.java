package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal wallet credit response used by payment-service outbox retry.
 *
 * @param walletId             credited wallet identifier
 * @param paymentTransactionId payment transaction identifier
 * @param amount               credited amount in whole IDR
 * @param balanceAfter         wallet balance after credit
 * @param creditedAt           credit timestamp
 */
public record InternalWalletCreditResponse(
		@JsonProperty("wallet_id") UUID walletId,
		@JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		Long amount,
		@JsonProperty("balance_after") Long balanceAfter,
		@JsonProperty("credited_at") Instant creditedAt) {
}
