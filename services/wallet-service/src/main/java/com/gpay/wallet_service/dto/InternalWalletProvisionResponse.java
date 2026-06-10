package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal wallet provisioning response with zero-balance wallet details.
 *
 * @param walletId  unique wallet identifier
 * @param userId    owning auth-service user identifier
 * @param balance   current balance in whole IDR
 * @param status    wallet operational status
 * @param createdAt wallet creation timestamp
 */
public record InternalWalletProvisionResponse(
		@JsonProperty("wallet_id") UUID walletId,
		@JsonProperty("user_id") UUID userId,
		Long balance,
		String status,
		@JsonProperty("created_at") Instant createdAt) {
}
