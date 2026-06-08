package com.gpay.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Registration response with newly created user data.
 *
 * @param userId    unique identifier of the created user
 * @param email     registered email address
 * @param createdAt timestamp when the user was created
 */
public record RegisterResponse(
		@JsonProperty("user_id") UUID userId,
		String email,
		@JsonProperty("created_at") Instant createdAt) {
}
