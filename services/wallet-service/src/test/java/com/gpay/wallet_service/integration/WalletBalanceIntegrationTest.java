package com.gpay.wallet_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Integration tests for wallet balance reads, including first-access provisioning.
 */
class WalletBalanceIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final WalletRepository walletRepository;

	@Autowired
	WalletBalanceIntegrationTest(MockMvc mockMvc, WalletRepository walletRepository) {
		this.mockMvc = mockMvc;
		this.walletRepository = walletRepository;
	}

	@Test
	void provisionsZeroBalanceWalletOnFirstRead() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet_id").isString())
				.andExpect(jsonPath("$.balance").value(0));

		assertThat(walletRepository.findByUserId(userId)).isPresent();
	}

	@Test
	void returnsExistingBalanceForProvisionedWallet() throws Exception {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		Wallet wallet = walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				userId,
				175_000L,
				WalletStatus.ACTIVE,
				now,
				now));

		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet_id").value(wallet.getId().toString()))
				.andExpect(jsonPath("$.balance").value(175000));
	}

	@Test
	void rejectsBalanceReadWhenTokenIsMissing() throws Exception {
		mockMvc.perform(get("/wallets/balance"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

	@Test
	void rejectsBalanceReadWhenTokenIsInvalid() throws Exception {
		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

	@Test
	void rejectsBalanceReadWhenAuthorizationSchemeIsNotBearer() throws Exception {
		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Token some-value"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}
}
