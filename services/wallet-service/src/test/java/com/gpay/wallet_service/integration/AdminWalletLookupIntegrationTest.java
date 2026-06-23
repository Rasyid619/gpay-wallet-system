package com.gpay.wallet_service.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for the admin wallet lookup endpoint, covering admin access to any owner's
 * wallet, the non-admin authorization guard, and the unknown-id branch against the real schema.
 */
class AdminWalletLookupIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final WalletRepository walletRepository;

	@Autowired
	AdminWalletLookupIntegrationTest(MockMvc mockMvc, WalletRepository walletRepository) {
		this.mockMvc = mockMvc;
		this.walletRepository = walletRepository;
	}

	private Wallet seedWallet(UUID ownerId, Long balance) {
		Instant now = Instant.now();
		return walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				ownerId,
				balance,
				WalletStatus.ACTIVE,
				now,
				now));
	}

	@Test
	void returnsAnyOwnersWalletForAdmin() throws Exception {
		UUID ownerId = UUID.randomUUID();
		Wallet seeded = seedWallet(ownerId, 175_000L);
		UUID adminId = UUID.randomUUID();

		mockMvc.perform(get("/admin/wallets/{id}", seeded.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(adminId, "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet_id").value(seeded.getId().toString()))
				.andExpect(jsonPath("$.balance").value(175000));
	}

	@Test
	void rejectsLookupForNonAdmin() throws Exception {
		Wallet seeded = seedWallet(UUID.randomUUID(), 50_000L);

		mockMvc.perform(get("/admin/wallets/{id}", seeded.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "USER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	@Test
	void returnsNotFoundForUnknownWallet() throws Exception {
		mockMvc.perform(get("/admin/wallets/{id}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "ADMIN")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"));
	}
}
