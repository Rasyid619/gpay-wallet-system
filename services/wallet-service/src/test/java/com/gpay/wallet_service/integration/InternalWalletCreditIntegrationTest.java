package com.gpay.wallet_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for internal wallet credit, covering top-up application, key idempotency,
 * payment-transaction idempotency, and the matching conflict branches.
 */
class InternalWalletCreditIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final WalletRepository walletRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	InternalWalletCreditIntegrationTest(
			MockMvc mockMvc,
			WalletRepository walletRepository,
			LedgerEntryRepository ledgerEntryRepository) {
		this.mockMvc = mockMvc;
		this.walletRepository = walletRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	private Wallet seedWallet(long balance) {
		Instant now = Instant.now();
		return walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				UUID.randomUUID(),
				balance,
				WalletStatus.ACTIVE,
				now,
				now));
	}

	private String creditBody(UUID walletId, UUID paymentTransactionId, long amount) {
		return """
				{
				  "wallet_id": "%s",
				  "payment_transaction_id": "%s",
				  "amount": %d
				}
				""".formatted(walletId, paymentTransactionId, amount);
	}

	@Test
	void creditsWalletForValidInternalRequest() throws Exception {
		Wallet wallet = seedWallet(100_000L);
		UUID paymentTransactionId = UUID.randomUUID();

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), paymentTransactionId, 50_000L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet_id").value(wallet.getId().toString()))
				.andExpect(jsonPath("$.payment_transaction_id").value(paymentTransactionId.toString()))
				.andExpect(jsonPath("$.amount").value(50000))
				.andExpect(jsonPath("$.balance_after").value(150000));

		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(150_000L);
		assertThat(ledgerEntryRepository.count()).isEqualTo(1);
	}

	@Test
	void replaysStoredResponseForRepeatedIdempotencyKey() throws Exception {
		Wallet wallet = seedWallet(100_000L);
		UUID paymentTransactionId = UUID.randomUUID();
		String body = creditBody(wallet.getId(), paymentTransactionId, 50_000L);

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-replay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-replay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance_after").value(150000));

		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(150_000L);
		assertThat(ledgerEntryRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsReusedIdempotencyKeyWithDifferentPayload() throws Exception {
		Wallet wallet = seedWallet(100_000L);

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-key-conflict")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), UUID.randomUUID(), 50_000L)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-key-conflict")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), UUID.randomUUID(), 70_000L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_CONFLICT"));
	}

	@Test
	void replaysCreditForRepeatedPaymentTransactionWithNewKey() throws Exception {
		Wallet wallet = seedWallet(100_000L);
		UUID paymentTransactionId = UUID.randomUUID();

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-pt-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), paymentTransactionId, 50_000L)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-pt-2")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), paymentTransactionId, 50_000L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance_after").value(150000));

		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(150_000L);
		assertThat(ledgerEntryRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsRepeatedPaymentTransactionWithDifferentAmount() throws Exception {
		Wallet wallet = seedWallet(100_000L);
		UUID paymentTransactionId = UUID.randomUUID();

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-pt-conflict-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), paymentTransactionId, 50_000L)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-pt-conflict-2")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), paymentTransactionId, 70_000L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("PAYMENT_TRANSACTION_CONFLICT"));
	}

	@Test
	void rejectsRepeatedPaymentTransactionForDifferentWallet() throws Exception {
		Wallet first = seedWallet(100_000L);
		Wallet second = seedWallet(100_000L);
		UUID paymentTransactionId = UUID.randomUUID();

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-pt-wallet-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(first.getId(), paymentTransactionId, 50_000L)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-pt-wallet-2")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(second.getId(), paymentTransactionId, 50_000L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("PAYMENT_TRANSACTION_CONFLICT"));
	}

	@Test
	void rejectsCreditWhenIdempotencyKeyIsBlank() throws Exception {
		Wallet wallet = seedWallet(100_000L);

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", " ")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), UUID.randomUUID(), 50_000L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsCreditForUnknownWallet() throws Exception {
		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.header("Idempotency-Key", "credit-unknown-wallet")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(UUID.randomUUID(), UUID.randomUUID(), 50_000L)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"));
	}

	@Test
	void rejectsCreditWhenInternalTokenIsInvalid() throws Exception {
		Wallet wallet = seedWallet(100_000L);

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", "wrong-token")
						.header("Idempotency-Key", "credit-bad-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), UUID.randomUUID(), 50_000L)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(100_000L);
	}

	@Test
	void rejectsCreditWhenIdempotencyKeyIsMissing() throws Exception {
		Wallet wallet = seedWallet(100_000L);

		mockMvc.perform(post("/internal/wallets/credit")
						.header("X-Internal-Token", INTERNAL_TOKEN)
						.contentType(MediaType.APPLICATION_JSON)
						.content(creditBody(wallet.getId(), UUID.randomUUID(), 50_000L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}
}
