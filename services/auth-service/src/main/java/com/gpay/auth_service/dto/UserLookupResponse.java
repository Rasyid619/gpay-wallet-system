package com.gpay.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Non-sensitive user view returned by the admin user lookup endpoint.
 *
 * <p>Deliberately omits credential material such as the password hash and refresh token
 * hashes, and never exposes the JPA entity.
 *
 * @param id        unique identifier of the user
 * @param email     registered email address
 * @param role      assigned authorization role
 * @param createdAt timestamp when the account was created
 */
public record UserLookupResponse(
		UUID id,
		String email,
		String role,
		@JsonProperty("created_at") Instant createdAt) {
}
