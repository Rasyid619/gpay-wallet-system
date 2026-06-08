package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Wallet ledger mutation response expressed in whole IDR.
 *
 * @param mutationId           unique ledger entry identifier
 * @param type                 debit or credit mutation direction
 * @param source               workflow that produced the mutation
 * @param amount               mutation amount in whole IDR
 * @param balanceAfter         wallet balance after the mutation
 * @param relatedTransactionId transfer or payment transaction identifier when available
 * @param createdAt            mutation creation timestamp
 */
public record WalletMutationResponse(
		@JsonProperty("mutation_id") UUID mutationId,
		String type,
		String source,
		Long amount,
		@JsonProperty("balance_after") Long balanceAfter,
		@JsonProperty("related_transaction_id") UUID relatedTransactionId,
		@JsonProperty("created_at") Instant createdAt) {
}
