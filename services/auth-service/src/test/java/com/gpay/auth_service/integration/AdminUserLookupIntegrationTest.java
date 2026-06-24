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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for the admin user lookup endpoint, covering admin access to any user, the
 * non-admin authorization guard, and the unknown-id branch against the real schema.
 */
class AdminUserLookupIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final UserRepository userRepository;
	private final JwtService jwtService;

	@Autowired
	AdminUserLookupIntegrationTest(MockMvc mockMvc, UserRepository userRepository, JwtService jwtService) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
	}

	private User seedUser(String email, UserRole role) {
		Instant now = Instant.now();
		return userRepository.save(User.create(
				UUID.randomUUID(),
				email,
				"$2a$10$hash",
				role,
				now,
				now));
	}

	@Test
	void returnsAnyUserForAdmin() throws Exception {
		User target = seedUser("target@example.com", UserRole.USER);
		String adminToken = jwtService.generateAccessToken(UUID.randomUUID(), "admin@example.com", UserRole.ADMIN);

		mockMvc.perform(get("/admin/users/{id}", target.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(target.getId().toString()))
				.andExpect(jsonPath("$.email").value("target@example.com"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.created_at").exists())
				.andExpect(jsonPath("$.password_hash").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.updated_at").doesNotExist());
	}

	@Test
	void rejectsLookupForNonAdmin() throws Exception {
		User target = seedUser("target@example.com", UserRole.USER);
		String userToken = jwtService.generateAccessToken(UUID.randomUUID(), "user@example.com", UserRole.USER);

		mockMvc.perform(get("/admin/users/{id}", target.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	@Test
	void returnsNotFoundForUnknownUser() throws Exception {
		String adminToken = jwtService.generateAccessToken(UUID.randomUUID(), "admin@example.com", UserRole.ADMIN);

		mockMvc.perform(get("/admin/users/{id}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}
}
