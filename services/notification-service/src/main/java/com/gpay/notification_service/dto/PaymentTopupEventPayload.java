package com.gpay.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Top-up result event consumed from payment-service topics.
 *
 * @param paymentTransactionId top-up transaction identifier
 * @param userId               auth-service user id who initiated the top-up
 * @param walletId             wallet intended to receive the top-up
 * @param amount               top-up amount in whole IDR
 * @param failureReason        gateway failure reason for failed top-ups, otherwise null
 */
public record PaymentTopupEventPayload(
		@JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		@JsonProperty("user_id") UUID userId,
		@JsonProperty("wallet_id") UUID walletId,
		Long amount,
		@JsonProperty("failure_reason") String failureReason) {
}
