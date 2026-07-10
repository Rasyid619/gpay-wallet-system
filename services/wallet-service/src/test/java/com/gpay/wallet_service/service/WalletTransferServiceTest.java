package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.gpay.wallet_service.constant.OutboxEventType;
import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.dto.IdempotentResponse;
import com.gpay.wallet_service.dto.TransferRequest;
import com.gpay.wallet_service.dto.TransferResponse;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.exception.BadRequestException;
import com.gpay.wallet_service.exception.IdempotencyConflictException;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import com.gpay.wallet_service.repository.OutboxEventRepository;
import com.gpay.wallet_service.repository.TransferRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import com.gpay.wallet_service.support.WalletPostgresContainer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration tests for atomic wallet transfer behavior.
 */
@SpringBootTest(properties = "wallet.transfer.max-daily-transfer-amount=100")
class WalletTransferServiceTest {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		WalletPostgresContainer.registerProperties(registry);
	}

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private TransferRepository transferRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private WalletTransferService walletTransferService;

	@Test
	void transfersBalanceAndCreatesLedgerEntries() {
		UUID senderUserId = UUID.randomUUID();
		Wallet senderWallet = saveWallet(senderUserId, 100L);
		Wallet receiverWallet = saveWallet(UUID.randomUUID(), 10L);

		IdempotentResponse result = walletTransferService.transfer(
				senderUserId,
				"transfer-success-" + UUID.randomUUID(),
				new TransferRequest(receiverWallet.getId(), 40L),
				"trace-success");

		assertThat(result.status()).isEqualTo(200);
		assertThat(result.body()).isInstanceOf(TransferResponse.class);
		TransferResponse body = (TransferResponse) result.body();
		assertThat(body.senderWalletId()).isEqualTo(senderWallet.getId());
		assertThat(body.receiverWalletId()).isEqualTo(receiverWallet.getId());
		assertThat(body.amount()).isEqualTo(40L);
		assertThat(body.status()).isEqualTo("SUCCESS");
		assertThat(walletRepository.findById(senderWallet.getId()).orElseThrow().getBalance()).isEqualTo(60L);
		assertThat(walletRepository.findById(receiverWallet.getId()).orElseThrow().getBalance()).isEqualTo(50L);
		assertThat(ledgerEntryRepository.findByWalletUserId(
				senderUserId,
				PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
				.getTotalElements()).isEqualTo(1);
		assertThat(ledgerEntryRepository.findByWalletUserId(
				receiverWallet.getUserId(),
				PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
				.getTotalElements()).isEqualTo(1);
	}

	@Test
	void successfulTransferEnqueuesSenderAndReceiverOutboxEvents() {
		UUID senderUserId = UUID.randomUUID();
		saveWallet(senderUserId, 100L);
		Wallet receiverWallet = saveWallet(UUID.randomUUID(), 10L);

		IdempotentResponse result = walletTransferService.transfer(
				senderUserId,
				"transfer-outbox-success-" + UUID.randomUUID(),
				new TransferRequest(receiverWallet.getId(), 40L),
				"trace-outbox-success");

		TransferResponse body = (TransferResponse) result.body();
		assertThat(outboxEventRepository.existsByAggregateIdAndEventType(
				body.transferId(), OutboxEventType.TRANSFER_COMPLETED)).isTrue();
		assertThat(outboxEventRepository.existsByAggregateIdAndEventType(
				body.transferId(), OutboxEventType.TRANSFER_RECEIVED)).isTrue();
	}

	@Test
	void failedTransferEnqueuesTransferFailedOutboxEvent() {
		UUID senderUserId = UUID.randomUUID();
		Wallet senderWallet = saveWallet(senderUserId, 30L);
		Wallet receiverWallet = saveWallet(UUID.randomUUID(), 10L);

		walletTransferService.transfer(
				senderUserId,
				"transfer-outbox-failed-" + UUID.randomUUID(),
				new TransferRequest(receiverWallet.getId(), 40L),
				"trace-outbox-failed");

		UUID failedTransferId = transferRepository.findAll()
				.stream()
				.filter(transfer -> transfer.getSenderWallet().getId().equals(senderWallet.getId()))
				.findFirst()
				.orElseThrow()
				.getId();
		assertThat(outboxEventRepository.existsByAggregateIdAndEventType(
				failedTransferId, OutboxEventType.TRANSFER_FAILED)).isTrue();
		assertThat(outboxEventRepository.existsByAggregateIdAndEventType(
				failedTransferId, OutboxEventType.TRANSFER_RECEIVED)).isFalse();
	}

	@Test
	void rejectsTransferWhenIdempotencyKeyIsNull() {
		UUID senderUserId = UUID.randomUUID();

		assertThatThrownBy(() -> walletTransferService.transfer(
				senderUserId,
				null,
				new TransferRequest(UUID.randomUUID(), 40L),
				"trace-null-key"))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void returnsInsufficientBalanceWithoutChangingBalances() {
		UUID senderUserId = UUID.randomUUID();
		Wallet senderWallet = saveWallet(senderUserId, 30L);
		Wallet receiverWallet = saveWallet(UUID.randomUUID(), 10L);

		IdempotentResponse result = walletTransferService.transfer(
				senderUserId,
				"transfer-insufficient-" + UUID.randomUUID(),
				new TransferRequest(receiverWallet.getId(), 40L),
				"trace-insufficient");

		assertThat(result.status()).isEqualTo(400);
		assertThat(result.body()).asString().contains("INSUFFICIENT_BALANCE");
		assertThat(walletRepository.findById(senderWallet.getId()).orElseThrow().getBalance()).isEqualTo(30L);
		assertThat(walletRepository.findById(receiverWallet.getId()).orElseThrow().getBalance()).isEqualTo(10L);
		assertThat(ledgerEntryRepository.findByWalletUserId(
				senderUserId,
				PageRequest.of(0, 20, Sort.unsorted()))
				.getTotalElements()).isZero();
	}

	@Test
	void returnsDailyLimitExceededWithoutChangingBalances() {
		UUID senderUserId = UUID.randomUUID();
		Wallet senderWallet = saveWallet(senderUserId, 200L);
		Wallet receiverWallet = saveWallet(UUID.randomUUID(), 0L);

		IdempotentResponse result = walletTransferService.transfer(
				senderUserId,
				"transfer-limit-" + UUID.randomUUID(),
				new TransferRequest(receiverWallet.getId(), 101L),
				"trace-limit");

		assertThat(result.status()).isEqualTo(400);
		assertThat(result.body()).asString().contains("DAILY_TRANSFER_LIMIT_EXCEEDED");
		assertThat(walletRepository.findById(senderWallet.getId()).orElseThrow().getBalance()).isEqualTo(200L);
		assertThat(walletRepository.findById(receiverWallet.getId()).orElseThrow().getBalance()).isZero();
	}

	@Test
	void rejectsTransferToSameWalletWithoutChangingBalance() {
		UUID senderUserId = UUID.randomUUID();
		Wallet senderWallet = saveWallet(senderUserId, 100L);

		IdempotentResponse result = walletTransferService.transfer(
				senderUserId,
				"transfer-same-wallet-" + UUID.randomUUID(),
				new TransferRequest(senderWallet.getId(), 40L),
				"trace-same-wallet");

		assertThat(result.status()).isEqualTo(400);
		assertThat(result.body()).asString().contains("SAME_WALLET_TRANSFER");
		assertThat(walletRepository.findById(senderWallet.getId()).orElseThrow().getBalance()).isEqualTo(100L);
		assertThat(ledgerEntryRepository.findByWalletUserId(
				senderUserId,
				PageRequest.of(0, 20, Sort.unsorted()))
				.getTotalElements()).isZero();
	}

	@Test
	void replaysStoredResponseForSameIdempotencyKeyAndPayload() {
		UUID senderUserId = UUID.randomUUID();
		Wallet senderWallet = saveWallet(senderUserId, 100L);
		Wallet receiverWallet = saveWallet(UUID.randomUUID(), 0L);
		String idempotencyKey = "transfer-idempotent-" + UUID.randomUUID();
		TransferRequest request = new TransferRequest(receiverWallet.getId(), 40L);

		IdempotentResponse first = walletTransferService.transfer(senderUserId, idempotencyKey, request, "trace-1");
		IdempotentResponse second = walletTransferService.transfer(senderUserId, idempotencyKey, request, "trace-2");

		assertThat(first.status()).isEqualTo(200);
		assertThat(second.status()).isEqualTo(200);
		assertThat(second.body()).isInstanceOf(JsonNode.class);
		assertThat(((JsonNode) second.body()).get("amount").asLong()).isEqualTo(40L);
		assertThat(walletRepository.findById(senderWallet.getId()).orElseThrow().getBalance()).isEqualTo(60L);
		assertThat(walletRepository.findById(receiverWallet.getId()).orElseThrow().getBalance()).isEqualTo(40L);
		assertThat(ledgerEntryRepository.findByWalletUserId(
				senderUserId,
				PageRequest.of(0, 20, Sort.unsorted()))
				.getTotalElements()).isEqualTo(1);
	}

	@Test
	void rejectsSameIdempotencyKeyWithDifferentPayload() {
		UUID senderUserId = UUID.randomUUID();
		Wallet receiverWallet = saveWallet(UUID.randomUUID(), 0L);
		Wallet otherReceiverWallet = saveWallet(UUID.randomUUID(), 0L);
		saveWallet(senderUserId, 100L);
		String idempotencyKey = "transfer-conflict-" + UUID.randomUUID();

		walletTransferService.transfer(
				senderUserId,
				idempotencyKey,
				new TransferRequest(receiverWallet.getId(), 40L),
				"trace-conflict");

		assertThatThrownBy(() -> walletTransferService.transfer(
				senderUserId,
				idempotencyKey,
				new TransferRequest(otherReceiverWallet.getId(), 40L),
				"trace-conflict"))
				.isInstanceOf(IdempotencyConflictException.class);
	}

	@Test
	void concurrentTransfersCannotOverspendSenderWallet() throws Exception {
		UUID senderUserId = UUID.randomUUID();
		Wallet senderWallet = saveWallet(senderUserId, 100L);
		Wallet firstReceiverWallet = saveWallet(UUID.randomUUID(), 0L);
		Wallet secondReceiverWallet = saveWallet(UUID.randomUUID(), 0L);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<IdempotentResponse> first = executor.submit(() -> transferAfterStart(
					ready,
					start,
					senderUserId,
					firstReceiverWallet.getId(),
					"transfer-concurrent-a-" + UUID.randomUUID()));
			Future<IdempotentResponse> second = executor.submit(() -> transferAfterStart(
					ready,
					start,
					senderUserId,
					secondReceiverWallet.getId(),
					"transfer-concurrent-b-" + UUID.randomUUID()));
			ready.await();
			start.countDown();

			List<Integer> statuses = List.of(first.get().status(), second.get().status());

			assertThat(statuses).containsExactlyInAnyOrder(200, 400);
			assertThat(walletRepository.findById(senderWallet.getId()).orElseThrow().getBalance()).isEqualTo(20L);
			Long receiverTotal = walletRepository.findById(firstReceiverWallet.getId()).orElseThrow().getBalance()
					+ walletRepository.findById(secondReceiverWallet.getId()).orElseThrow().getBalance();
			assertThat(receiverTotal).isEqualTo(80L);
		} finally {
			executor.shutdownNow();
		}
	}

	private IdempotentResponse transferAfterStart(
			CountDownLatch ready,
			CountDownLatch start,
			UUID senderUserId,
			UUID receiverWalletId,
			String idempotencyKey) throws Exception {
		ready.countDown();
		start.await();
		return walletTransferService.transfer(
				senderUserId,
				idempotencyKey,
				new TransferRequest(receiverWalletId, 80L),
				"trace-concurrent");
	}

	private Wallet saveWallet(UUID userId, Long balance) {
		Instant now = Instant.now();
		return walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				userId,
				balance,
				WalletStatus.ACTIVE,
				now,
				now));
	}
}
