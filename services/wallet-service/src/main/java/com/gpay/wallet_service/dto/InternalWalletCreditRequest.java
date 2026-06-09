package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Internal wallet credit request from payment-service outbox delivery.
 *
 * @param walletId              wallet receiving the credit
 * @param paymentTransactionId  payment transaction identifier used for idempotency
 * @param amount                credit amount in whole IDR
 */
public record InternalWalletCreditRequest(
		@NotNull @JsonProperty("wallet_id") UUID walletId,
		@NotNull @JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		@NotNull @Positive Long amount) {
}
