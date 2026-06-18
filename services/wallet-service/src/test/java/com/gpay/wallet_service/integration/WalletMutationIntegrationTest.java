package com.gpay.wallet_service.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.wallet_service.constant.LedgerEntrySource;
import com.gpay.wallet_service.constant.LedgerEntryType;
import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.entity.LedgerEntry;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for paginated wallet mutation history reads.
 */
class WalletMutationIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final WalletRepository walletRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	WalletMutationIntegrationTest(
			MockMvc mockMvc,
			WalletRepository walletRepository,
			LedgerEntryRepository ledgerEntryRepository) {
		this.mockMvc = mockMvc;
		this.walletRepository = walletRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	@Test
	void returnsNewestMutationFirstForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		Wallet wallet = walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				userId,
				150_000L,
				WalletStatus.ACTIVE,
				now,
				now));

		UUID olderPaymentId = UUID.randomUUID();
		UUID newerPaymentId = UUID.randomUUID();
		ledgerEntryRepository.save(LedgerEntry.create(
				UUID.randomUUID(),
				wallet,
				null,
				olderPaymentId,
				LedgerEntryType.CREDIT,
				LedgerEntrySource.TOP_UP,
				50_000L,
				50_000L,
				"Top-up received",
				now.minusSeconds(60)));
		ledgerEntryRepository.save(LedgerEntry.create(
				UUID.randomUUID(),
				wallet,
				null,
				newerPaymentId,
				LedgerEntryType.CREDIT,
				LedgerEntrySource.TOP_UP,
				100_000L,
				150_000L,
				"Top-up received",
				now));

		mockMvc.perform(get("/wallets/mutations?page=0&size=10")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.total_items").value(2))
				.andExpect(jsonPath("$.items[0].related_transaction_id").value(newerPaymentId.toString()))
				.andExpect(jsonPath("$.items[0].amount").value(100000))
				.andExpect(jsonPath("$.items[0].balance_after").value(150000))
				.andExpect(jsonPath("$.items[0].type").value("CREDIT"))
				.andExpect(jsonPath("$.items[0].source").value("TOP_UP"))
				.andExpect(jsonPath("$.items[1].related_transaction_id").value(olderPaymentId.toString()));
	}

	@Test
	void excludesMutationsOwnedByOtherUsers() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		Instant now = Instant.now();
		walletRepository.save(Wallet.create(UUID.randomUUID(), userId, 0L, WalletStatus.ACTIVE, now, now));
		Wallet otherWallet = walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				otherUserId,
				50_000L,
				WalletStatus.ACTIVE,
				now,
				now));
		ledgerEntryRepository.save(LedgerEntry.create(
				UUID.randomUUID(),
				otherWallet,
				null,
				UUID.randomUUID(),
				LedgerEntryType.CREDIT,
				LedgerEntrySource.TOP_UP,
				50_000L,
				50_000L,
				"Top-up received",
				now));

		mockMvc.perform(get("/wallets/mutations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total_items").value(0));
	}

	@Test
	void capsPageSizeAtTheMaximumForOversizedRequests() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(get("/wallets/mutations?page=0&size=500")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
	}

	@Test
	void rejectsMutationReadWhenTokenIsMissing() throws Exception {
		mockMvc.perform(get("/wallets/mutations"))
				.andExpect(status().isUnauthorized());
	}
}
