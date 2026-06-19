package com.gpay.wallet_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import com.gpay.wallet_service.repository.TransferRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for authenticated wallet-to-wallet transfers, covering the happy path,
 * documented business failures, idempotency replay/conflict, and lock-protected concurrency.
 */
class WalletTransferIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final WalletRepository walletRepository;
	private final TransferRepository transferRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	WalletTransferIntegrationTest(
			MockMvc mockMvc,
			WalletRepository walletRepository,
			TransferRepository transferRepository,
			LedgerEntryRepository ledgerEntryRepository) {
		this.mockMvc = mockMvc;
		this.walletRepository = walletRepository;
		this.transferRepository = transferRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	private Wallet seedWallet(UUID userId, long balance) {
		Instant now = Instant.now();
		return walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				userId,
				balance,
				WalletStatus.ACTIVE,
				now,
				now));
	}

	private String transferBody(UUID receiverWalletId, long amount) {
		return """
				{
				  "receiver_wallet_id": "%s",
				  "amount": %d
				}
				""".formatted(receiverWalletId, amount);
	}

	@Test
	void movesMoneyBetweenWalletsForValidTransfer() throws Exception {
		UUID userId = UUID.randomUUID();
		Wallet sender = seedWallet(userId, 50_000L);
		Wallet receiver = seedWallet(UUID.randomUUID(), 0L);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "transfer-success")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(receiver.getId(), 30_000L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sender_wallet_id").value(sender.getId().toString()))
				.andExpect(jsonPath("$.receiver_wallet_id").value(receiver.getId().toString()))
				.andExpect(jsonPath("$.amount").value(30000))
				.andExpect(jsonPath("$.status").value("SUCCESS"));

		assertThat(walletRepository.findById(sender.getId()).orElseThrow().getBalance()).isEqualTo(20_000L);
		assertThat(walletRepository.findById(receiver.getId()).orElseThrow().getBalance()).isEqualTo(30_000L);
		assertThat(ledgerEntryRepository.count()).isEqualTo(2);
	}

	@Test
	void rejectsTransferWhenBalanceIsNotEnough() throws Exception {
		UUID userId = UUID.randomUUID();
		Wallet sender = seedWallet(userId, 10_000L);
		Wallet receiver = seedWallet(UUID.randomUUID(), 0L);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "transfer-insufficient")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(receiver.getId(), 30_000L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"));

		assertThat(walletRepository.findById(sender.getId()).orElseThrow().getBalance()).isEqualTo(10_000L);
		assertThat(transferRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsTransferToSameWallet() throws Exception {
		UUID userId = UUID.randomUUID();
		Wallet sender = seedWallet(userId, 50_000L);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "transfer-same-wallet")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(sender.getId(), 10_000L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("SAME_WALLET_TRANSFER"));
	}

	@Test
	void rejectsTransferWhenReceiverWalletDoesNotExist() throws Exception {
		UUID userId = UUID.randomUUID();
		seedWallet(userId, 50_000L);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "transfer-missing-receiver")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(UUID.randomUUID(), 10_000L)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"));
	}

	@Test
	void rejectsTransferThatExceedsDailyLimit() throws Exception {
		UUID userId = UUID.randomUUID();
		seedWallet(userId, 5_000_000L);
		Wallet receiver = seedWallet(UUID.randomUUID(), 0L);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", "transfer-daily-limit")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(receiver.getId(), MAX_DAILY_TRANSFER_AMOUNT + 1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("DAILY_TRANSFER_LIMIT_EXCEEDED"));
	}

	@Test
	void replaysStoredResponseForRepeatedIdempotencyKeyAndPayload() throws Exception {
		UUID userId = UUID.randomUUID();
		Wallet sender = seedWallet(userId, 50_000L);
		Wallet receiver = seedWallet(UUID.randomUUID(), 0L);
		String token = "Bearer " + createToken(userId);
		String body = transferBody(receiver.getId(), 30_000L);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, token)
						.header("Idempotency-Key", "transfer-replay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, token)
						.header("Idempotency-Key", "transfer-replay")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUCCESS"));

		assertThat(walletRepository.findById(sender.getId()).orElseThrow().getBalance()).isEqualTo(20_000L);
		assertThat(transferRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsReusedIdempotencyKeyWithDifferentPayload() throws Exception {
		UUID userId = UUID.randomUUID();
		seedWallet(userId, 50_000L);
		Wallet receiver = seedWallet(UUID.randomUUID(), 0L);
		String token = "Bearer " + createToken(userId);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, token)
						.header("Idempotency-Key", "transfer-conflict")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(receiver.getId(), 30_000L)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, token)
						.header("Idempotency-Key", "transfer-conflict")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(receiver.getId(), 10_000L)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_CONFLICT"));
	}

	@Test
	void rejectsTransferWhenIdempotencyKeyIsMissing() throws Exception {
		UUID userId = UUID.randomUUID();
		seedWallet(userId, 50_000L);
		Wallet receiver = seedWallet(UUID.randomUUID(), 0L);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(receiver.getId(), 30_000L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsReusedIdempotencyKeyAcrossDifferentUsers() throws Exception {
		UUID firstUser = UUID.randomUUID();
		UUID secondUser = UUID.randomUUID();
		seedWallet(firstUser, 50_000L);
		seedWallet(secondUser, 50_000L);
		Wallet receiver = seedWallet(UUID.randomUUID(), 0L);
		String body = transferBody(receiver.getId(), 30_000L);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(firstUser))
						.header("Idempotency-Key", "transfer-shared-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(secondUser))
						.header("Idempotency-Key", "transfer-shared-key")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_CONFLICT"));
	}

	@Test
	void rejectsTransferWhenIdempotencyKeyIsBlank() throws Exception {
		UUID userId = UUID.randomUUID();
		seedWallet(userId, 50_000L);
		Wallet receiver = seedWallet(UUID.randomUUID(), 0L);

		mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId))
						.header("Idempotency-Key", " ")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(receiver.getId(), 30_000L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsTransferWhenTokenIsMissing() throws Exception {
		mockMvc.perform(post("/wallets/transfer")
						.header("Idempotency-Key", "transfer-no-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content(transferBody(UUID.randomUUID(), 30_000L)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void appliesExactlyOneTransferWhenSameKeyIsSentConcurrently() throws Exception {
		UUID userId = UUID.randomUUID();
		Wallet sender = seedWallet(userId, 50_000L);
		Wallet receiver = seedWallet(UUID.randomUUID(), 0L);
		String token = "Bearer " + createToken(userId);
		String body = transferBody(receiver.getId(), 30_000L);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		int firstStatus;
		int secondStatus;
		try {
			Future<Integer> first = executor.submit(() -> performTransfer(token, "transfer-concurrent", body));
			Future<Integer> second = executor.submit(() -> performTransfer(token, "transfer-concurrent", body));
			firstStatus = first.get();
			secondStatus = second.get();
		} finally {
			executor.shutdownNow();
		}

		// The losing request is handled gracefully: it either replays the stored response (200)
		// or loses the idempotency-key race and is mapped to a clean 409 — never a 5xx error.
		assertThat(firstStatus).isIn(200, 409);
		assertThat(secondStatus).isIn(200, 409);
		assertThat(List.of(firstStatus, secondStatus)).contains(200);
		assertThat(walletRepository.findById(sender.getId()).orElseThrow().getBalance()).isEqualTo(20_000L);
		assertThat(walletRepository.findById(receiver.getId()).orElseThrow().getBalance()).isEqualTo(30_000L);
		assertThat(transferRepository.count()).isEqualTo(1);
	}

	/*
	 * Sends one concurrent transfer attempt and returns the resulting HTTP status. The wallet
	 * row lock serializes the two requests; the loser either replays (200) or loses the
	 * idempotency-key insert race and is mapped to 409.
	 */
	private int performTransfer(String token, String idempotencyKey, String body) throws Exception {
		return mockMvc.perform(post("/wallets/transfer")
						.header(HttpHeaders.AUTHORIZATION, token)
						.header("Idempotency-Key", idempotencyKey)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andReturn()
				.getResponse()
				.getStatus();
	}
}
