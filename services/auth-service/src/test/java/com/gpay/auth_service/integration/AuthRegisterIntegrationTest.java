package com.gpay.auth_service.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.repository.UserRepository;
import com.gpay.auth_service.service.WalletProvisioningClient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for registration across HTTP, persistence, and the wallet-provisioning boundary.
 */
class AuthRegisterIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final UserRepository userRepository;

	@MockitoBean
	private WalletProvisioningClient walletProvisioningClient;

	@Autowired
	AuthRegisterIntegrationTest(MockMvc mockMvc, UserRepository userRepository) {
		this.mockMvc = mockMvc;
		this.userRepository = userRepository;
	}

	@Test
	void registerCreatesUserAndProvisionsWallet() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "new-user@example.com",
								  "password": "Password1!"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user_id").isString())
				.andExpect(jsonPath("$.email").value("new-user@example.com"))
				.andExpect(jsonPath("$.created_at").isString());

		verify(walletProvisioningClient).provisionWallet(any(UUID.class), any());
	}

	@Test
	void registerRejectsDuplicateEmail() throws Exception {
		Instant now = Instant.now();
		userRepository.save(User.create(
				UUID.randomUUID(),
				"taken@example.com",
				"$2a$10$existinghash",
				UserRole.USER,
				now,
				now));

		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "taken@example.com",
								  "password": "Password1!"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void registerRejectsWeakPasswordWithValidationError() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "weak@example.com",
								  "password": "weak"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}
}
