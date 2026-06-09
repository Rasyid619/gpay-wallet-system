package com.gpay.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record GatewayWebhookResponse(
		@JsonProperty("payment_transaction_id") UUID paymentTransactionId,
		String status) {
}
