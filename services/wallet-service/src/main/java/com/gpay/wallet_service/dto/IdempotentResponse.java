package com.gpay.wallet_service.dto;

/* HTTP status and body returned by an idempotent wallet command. */
public record IdempotentResponse(
		Integer status,
		Object body) {
}
