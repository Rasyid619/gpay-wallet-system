package com.gpay.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Current user profile response for GET /auth/me.
 *
 * @param userId    unique identifier of the authenticated user
 * @param email     registered email address
 * @param role      assigned authorization role
 * @param createdAt timestamp when the account was created
 */
public record UserMeResponse(
		@JsonProperty("user_id") UUID userId,
		String email,
		String role,
		@JsonProperty("created_at") Instant createdAt) {
}
