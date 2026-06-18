package com.gpay.auth_service.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.repository.UserRepository;
import com.gpay.auth_service.security.JwtService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for current-user retrieval across HTTP, JWT filtering, and persistence.
 */
class AuthMeIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private User user;

	@Autowired
	AuthMeIntegrationTest(MockMvc mockMvc, UserRepository userRepository, JwtService jwtService) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
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
	void meReturnsAuthenticatedUser() throws Exception {
		String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

		mockMvc.perform(get("/auth/me")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user_id").value(user.getId().toString()))
				.andExpect(jsonPath("$.email").value("user@example.com"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void meRejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(get("/auth/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meRejectsNonBearerAuthorizationHeader() throws Exception {
		mockMvc.perform(get("/auth/me")
						.header("Authorization", "Basic dXNlcjpwYXNz"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meRejectsInvalidToken() throws Exception {
		mockMvc.perform(get("/auth/me")
						.header("Authorization", "Bearer not-a-valid-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void meReturnsNotFoundWhenAuthenticatedUserNoLongerExists() throws Exception {
		String accessToken = jwtService.generateAccessToken(UUID.randomUUID(), "ghost@example.com");

		mockMvc.perform(get("/auth/me")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}
}
