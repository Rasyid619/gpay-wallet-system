package com.gpay.auth_service.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for the internal user lookup endpoint, covering trusted-token access,
 * the invalid-token guard, and the unknown-id branch against the real schema.
 */
class InternalUserLookupIntegrationTest extends AbstractIntegrationTest {

	private static final String INTERNAL_TOKEN = "integration-test-auth-internal-token";

	private final MockMvc mockMvc;
	private final UserRepository userRepository;

	@Autowired
	InternalUserLookupIntegrationTest(MockMvc mockMvc, UserRepository userRepository) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
	}

	@DynamicPropertySource
	static void configureInternalToken(DynamicPropertyRegistry registry) {
		registry.add("auth.internal-token", () -> INTERNAL_TOKEN);
	}

	private User seedUser(String email) {
		Instant now = Instant.now();
		return userRepository.save(User.create(
				UUID.randomUUID(),
				email,
				"$2a$10$hash",
				UserRole.USER,
				now,
				now));
	}

	@Test
	void returnsIdAndEmailForTrustedInternalCaller() throws Exception {
		User target = seedUser("recipient@example.com");

		mockMvc.perform(get("/internal/users/{id}", target.getId())
						.header("X-Internal-Token", INTERNAL_TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(target.getId().toString()))
				.andExpect(jsonPath("$.email").value("recipient@example.com"))
				.andExpect(jsonPath("$.role").doesNotExist())
				.andExpect(jsonPath("$.password_hash").doesNotExist());
	}

	@Test
	void rejectsLookupWithInvalidInternalToken() throws Exception {
		User target = seedUser("recipient@example.com");

		mockMvc.perform(get("/internal/users/{id}", target.getId())
						.header("X-Internal-Token", "wrong-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

	@Test
	void returnsNotFoundForUnknownUser() throws Exception {
		mockMvc.perform(get("/internal/users/{id}", UUID.randomUUID())
						.header("X-Internal-Token", INTERNAL_TOKEN))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}
}
