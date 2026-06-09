package com.gpay.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment top-up response.
 *
 * @param paymentTransactionId unique payment transaction identifier
 * @param walletId              wallet intended to receive the top-up
 * @param amount                top-up amount in whole IDR
 * @param status                payment transaction status
 * @param createdAt             transaction creation timestamp
 */
public record TopUpResponse(
		@JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		@JsonProperty("wallet_id") UUID walletId,
		Long amount,
		String status,
		@JsonProperty("created_at") Instant createdAt) {
}
