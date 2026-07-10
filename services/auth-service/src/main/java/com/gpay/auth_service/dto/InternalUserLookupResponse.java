package com.gpay.auth_service.dto;

import java.util.UUID;

/**
 * Narrow user view returned to trusted internal services, limited to the
 * fields needed for cross-service workflows such as email notifications.
 *
 * @param id    unique identifier of the user
 * @param email registered email address
 */
public record InternalUserLookupResponse(
		UUID id,
		String email) {
}
