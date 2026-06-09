package com.gpay.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Payment top-up request payload.
 *
 * @param walletId wallet intended to receive the top-up
 * @param amount   top-up amount in whole IDR
 */
public record TopUpRequest(
		@NotNull @JsonProperty("wallet_id") UUID walletId,
		@NotNull @Positive Long amount) {
}
