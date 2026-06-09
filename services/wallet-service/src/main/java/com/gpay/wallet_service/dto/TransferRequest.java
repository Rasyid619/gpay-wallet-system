package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Wallet transfer request payload.
 *
 * @param receiverWalletId wallet receiving the credit
 * @param amount           transfer amount in whole IDR
 */
public record TransferRequest(
		@NotNull @JsonProperty("receiver_wallet_id") UUID receiverWalletId,
		@NotNull @Positive Long amount) {
}
