package com.gpay.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login request payload.
 *
 * @param email    registered user email
 * @param password plain-text password to verify
 */
public record LoginRequest(
		@NotBlank @Email String email,
		@NotBlank String password) {
}
