package com.gpay.payment_service.dto;

/**
 * Stored idempotent response wrapper.
 *
 * @param status HTTP status code to return
 * @param body   response body to return
 */
public record IdempotentResponse(
		Integer status,
		Object body) {
}
