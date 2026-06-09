package com.gpay.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpay.payment_service.constant.PaymentGatewayMode;
import java.util.UUID;

public record GatewayTopUpRequest(
		@JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		@JsonProperty("wallet_id") UUID walletId,
		Long amount,
		@JsonProperty("mode") PaymentGatewayMode mode) {
}
