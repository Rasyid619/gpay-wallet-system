package com.gpay.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record WalletCreditOutboxPayload(
		@JsonProperty("wallet_id") UUID walletId,
		@JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		Long amount) {
}
