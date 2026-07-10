package com.gpay.notification_service.dto;

import java.util.UUID;

/**
 * Auth-service internal user lookup response.
 *
 * @param id    unique identifier of the user
 * @param email registered email address
 */
public record UserEmailLookupResponse(
		UUID id,
		String email) {
}
