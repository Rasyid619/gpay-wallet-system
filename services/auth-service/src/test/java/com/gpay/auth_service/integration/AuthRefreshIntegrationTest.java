package com.gpay.auth_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.auth_service.entity.RefreshToken;
import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.repository.RefreshTokenRepository;
import com.gpay.auth_service.repository.UserRepository;
import com.gpay.auth_service.security.HashUtil;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for refresh-token rotation across HTTP, persistence, and token validation.
 */
class AuthRefreshIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private User user;

	@Autowired
	AuthRefreshIntegrationTest(
			MockMvc mockMvc,
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository
	) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@BeforeEach
	void seedUser() {
		Instant now = Instant.now();
		user = userRepository.save(User.create(
				UUID.randomUUID(),
				"user@example.com",
				"$2a$10$hash",
				UserRole.USER,
				now,
				now));
	}

	@Test
	void refreshRotatesTokensAndRevokesOldTokenForValidRefreshToken() throws Exception {
		String rawToken = "valid-raw-refresh-token";
		RefreshToken stored = seedRefreshToken(rawToken, Instant.now().plus(7, ChronoUnit.DAYS), false);

		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshBody(rawToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").isString())
				.andExpect(jsonPath("$.refresh_token").isString());

		assertThat(refreshTokenRepository.findById(stored.getId()).orElseThrow().isRevoked()).isTrue();
		assertThat(refreshTokenRepository.count()).isEqualTo(2);
	}

	@Test
	void refreshRejectsUnknownToken() throws Exception {
		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshBody("unknown-token")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid refresh token"));
	}

	@Test
	void refreshRejectsRevokedToken() throws Exception {
		String rawToken = "revoked-raw-token";
		seedRefreshToken(rawToken, Instant.now().plus(7, ChronoUnit.DAYS), true);

		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshBody(rawToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Refresh token has been revoked"));
	}

	@Test
	void refreshRejectsExpiredToken() throws Exception {
		String rawToken = "expired-raw-token";
		seedRefreshToken(rawToken, Instant.now().minus(1, ChronoUnit.DAYS), false);

		mockMvc.perform(post("/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshBody(rawToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Refresh token has expired"));
	}

	private RefreshToken seedRefreshToken(String rawToken, Instant expiresAt, boolean revoked) {
		RefreshToken token = RefreshToken.create(
				UUID.randomUUID(),
				user,
				HashUtil.sha256(rawToken),
				expiresAt,
				Instant.now());
		if (revoked) {
			token.revoke(Instant.now());
		}
		return refreshTokenRepository.save(token);
	}

	private String refreshBody(String rawToken) {
		return "{\"refresh_token\": \"" + rawToken + "\"}";
	}
}
