package com.gpay.wallet_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.wallet_service.repository.WalletRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for internal wallet provisioning, including idempotent re-provisioning.
 */
class InternalWalletProvisionIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final WalletRepository walletRepository;

	@Autowired
	InternalWalletProvisionIntegrationTest(MockMvc mockMvc, WalletRepository walletRepository) {
		this.mockMvc = mockMvc;
		this.walletRepository = walletRepository;
	}

	private String provisionBody(UUID userId) {
		return """
				{
				  "user_id": "%s"
				}
				""".formatted(userId);
	}

	@Test
	void provisionsZeroBalanceWalletForUser() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(post("/internal/wallets/provision")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content(provisionBody(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user_id").value(userId.toString()))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		assertThat(walletRepository.findByUserId(userId)).isPresent();
	}

	@Test
	void returnsExistingWalletWhenUserIsAlreadyProvisioned() throws Exception {
		UUID userId = UUID.randomUUID();
		String body = provisionBody(userId);

		mockMvc.perform(post("/internal/wallets/provision")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());

		mockMvc.perform(post("/internal/wallets/provision")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user_id").value(userId.toString()));

		assertThat(walletRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsProvisionWhenInternalTokenIsInvalid() throws Exception {
		mockMvc.perform(post("/internal/wallets/provision")
						.header("X-Internal-Token", "wrong-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content(provisionBody(UUID.randomUUID())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

		assertThat(walletRepository.count()).isZero();
	}
}
