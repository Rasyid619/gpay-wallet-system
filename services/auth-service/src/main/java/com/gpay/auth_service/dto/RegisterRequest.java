package com.gpay.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request payload.
 *
 * @param email    unique email address for the new account
 * @param password plain-text password; minimum 8 characters
 */
public record RegisterRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8) String password) {
}
