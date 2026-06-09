package com.gpay.mock_gateway_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpay.mock_gateway_service.constant.GatewayMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record MockGatewayTopUpRequest(
		@NotNull @JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		@NotNull @JsonProperty("wallet_id") UUID walletId,
		@NotNull @Positive Long amount,
		@NotNull GatewayMode mode) {
}
