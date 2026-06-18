package com.gpay.auth_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.repository.RefreshTokenRepository;
import com.gpay.auth_service.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for login behavior across HTTP, persistence, and refresh-token issuance.
 */
class AuthLoginIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	AuthLoginIntegrationTest(
			MockMvc mockMvc,
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder
	) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@BeforeEach
	void seedUser() {
		Instant now = Instant.now();
		userRepository.save(User.create(
				UUID.randomUUID(),
				"user@example.com",
				passwordEncoder.encode("Password1!"),
				UserRole.USER,
				now,
				now));
	}

	@Test
	void loginReturnsTokensAndPersistsRefreshTokenForValidCredentials() throws Exception {
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "user@example.com",
								  "password": "Password1!"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").isString())
				.andExpect(jsonPath("$.refresh_token").isString());

		assertThat(refreshTokenRepository.count()).isEqualTo(1);
	}

	@Test
	void loginRejectsInvalidCredentials() throws Exception {
		mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "user@example.com",
								  "password": "WrongPassword1!"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}
}
