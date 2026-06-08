package com.gpay.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Refresh token request payload.
 *
 * @param refreshToken the opaque refresh token issued at login
 */
public record RefreshRequest(
		@NotBlank @JsonProperty("refresh_token") String refreshToken) {
}
