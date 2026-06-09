package com.gpay.mock_gateway_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record MockGatewayTopUpResponse(
		@JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		@JsonProperty("wallet_id") UUID walletId,
		Long amount,
		String mode,
		String status,
		@JsonProperty("gateway_reference") String gatewayReference,
		String message) {
}
