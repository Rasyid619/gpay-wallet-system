package com.gpay.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Transfer result event consumed from wallet-service topics.
 *
 * @param transferId       transfer identifier
 * @param senderWalletId   debited wallet identifier
 * @param receiverWalletId credited wallet identifier
 * @param userId           auth-service user id of the transfer initiator
 * @param amount           transfer amount in whole IDR
 * @param failureReason    rejection reason for failed transfers, otherwise null
 */
public record WalletTransferEventPayload(
		@JsonProperty("transfer_id") UUID transferId,
		@JsonProperty("sender_wallet_id") UUID senderWalletId,
		@JsonProperty("receiver_wallet_id") UUID receiverWalletId,
		@JsonProperty("user_id") UUID userId,
		Long amount,
		@JsonProperty("failure_reason") String failureReason) {
}
