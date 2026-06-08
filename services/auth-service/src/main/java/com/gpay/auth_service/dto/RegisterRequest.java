package com.gpay.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Registration request payload.
 *
 * <p>Password rules: minimum 8 characters, at least one uppercase letter,
 * one lowercase letter, one digit, and one special character.
 *
 * @param email    unique email address for the new account
 * @param password plain-text password meeting complexity requirements
 */
public record RegisterRequest(
		@NotBlank @Email String email,
		@NotBlank
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d\\s]).{8,}$",
				message = "must be at least 8 characters and contain uppercase, lowercase, digit, and special character")
		String password) {
}
