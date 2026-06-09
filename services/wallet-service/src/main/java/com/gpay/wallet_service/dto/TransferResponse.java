package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Wallet transfer response.
 *
 * @param transferId       unique transfer identifier
 * @param senderWalletId   debited wallet identifier
 * @param receiverWalletId credited wallet identifier
 * @param amount           transferred amount in whole IDR
 * @param status           transfer status
 * @param createdAt        transfer creation timestamp
 */
public record TransferResponse(
		@JsonProperty("transfer_id") UUID transferId,
		@JsonProperty("sender_wallet_id") UUID senderWalletId,
		@JsonProperty("receiver_wallet_id") UUID receiverWalletId,
		Long amount,
		String status,
		@JsonProperty("created_at") Instant createdAt) {
}
