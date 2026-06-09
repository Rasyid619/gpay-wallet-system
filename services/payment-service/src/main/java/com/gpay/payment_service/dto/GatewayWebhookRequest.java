package com.gpay.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpay.payment_service.constant.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record GatewayWebhookRequest(
		@NotNull @JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		@NotNull @JsonProperty("wallet_id") UUID walletId,
		@NotNull @Positive Long amount,
		@NotNull PaymentStatus status,
		@NotNull @JsonProperty("gateway_reference") String gatewayReference) {
}
