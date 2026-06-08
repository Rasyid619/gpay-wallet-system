package com.gpay.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Login response containing JWT tokens.
 *
 * @param accessToken  short-lived JWT access token (15 minutes)
 * @param refreshToken long-lived opaque refresh token (7 days)
 */
public record LoginResponse(
		@JsonProperty("access_token") String accessToken,
		@JsonProperty("refresh_token") String refreshToken) {
}
