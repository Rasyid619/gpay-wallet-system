package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.wallet_service.constant.OutboxEventType;
import com.gpay.wallet_service.constant.TransferStatus;
import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.dto.TransferEventOutboxPayload;
import com.gpay.wallet_service.entity.OutboxEvent;
import com.gpay.wallet_service.entity.Transfer;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.OutboxEventRepository;
import com.gpay.wallet_service.repository.TransferRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import com.gpay.wallet_service.support.WalletPostgresContainer;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for the transfer-event outbox worker, covering acked
 * publishes, retryable failures, exhausted budgets, max age, and stale
 * PROCESSING recovery against the real schema.
 */
@SpringBootTest
class WalletOutboxWorkerTest {

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		WalletPostgresContainer.registerProperties(registry);
		registry.add("wallet.outbox.max-attempts", () -> "3");
	}

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private TransferRepository transferRepository;

	@Autowired
	private WalletOutboxWorker walletOutboxWorker;

	@Autowired
	private WalletRepository walletRepository;

	@MockitoBean
	private TransferEventPublisher transferEventPublisher;

	@Test
	void successfulPublishMarksOutboxEventProcessed() throws Exception {
		Transfer transfer = savedTransfer();
		OutboxEvent event = pendingOutboxEvent(transfer, OutboxEventType.TRANSFER_COMPLETED, Instant.now());

		walletOutboxWorker.processPendingTransferEvents();

		ArgumentCaptor<TransferEventOutboxPayload> payloadCaptor =
				ArgumentCaptor.forClass(TransferEventOutboxPayload.class);
		verify(transferEventPublisher).publish(
				eq(OutboxEventType.TRANSFER_COMPLETED),
				payloadCaptor.capture(),
				eq("wallet-outbox-" + event.getId()),
				eq("trace-outbox"));
		assertThat(payloadCaptor.getValue().transferId()).isEqualTo(transfer.getId());
		assertThat(payloadCaptor.getValue().amount()).isEqualTo(40_000L);

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PROCESSED");
		assertThat(updated.getLastError()).isNull();
		assertThat(updated.getNextRetryAt()).isNull();
	}

	@Test
	void publishFailureRemainsRetryableWithFailureMetadata() throws Exception {
		Transfer transfer = savedTransfer();
		OutboxEvent event = pendingOutboxEvent(transfer, OutboxEventType.TRANSFER_COMPLETED, Instant.now());
		doThrow(new RuntimeException("broker unavailable"))
				.when(transferEventPublisher)
				.publish(any(), any(TransferEventOutboxPayload.class), any(String.class), any());

		walletOutboxWorker.processPendingTransferEvents();

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PENDING");
		assertThat(updated.getRetryCount()).isEqualTo(1);
		assertThat(updated.getLastError()).isEqualTo("broker unavailable");
		assertThat(updated.getNextRetryAt()).isAfter(Instant.now());
	}

	@Test
	void exhaustedRetriesMarkOutboxEventFailed() throws Exception {
		Transfer transfer = savedTransfer();
		OutboxEvent event = pendingOutboxEvent(transfer, OutboxEventType.TRANSFER_COMPLETED, Instant.now());
		Instant past = Instant.now().minusSeconds(1);
		event.markFailedAttempt("attempt-1", past, past);
		event.markFailedAttempt("attempt-2", past, past);
		outboxEventRepository.saveAndFlush(event);
		doThrow(new RuntimeException("broker still unavailable"))
				.when(transferEventPublisher)
				.publish(any(), any(TransferEventOutboxPayload.class), any(String.class), any());

		walletOutboxWorker.processPendingTransferEvents();

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("FAILED");
		assertThat(updated.getRetryCount()).isEqualTo(3);
		assertThat(updated.getLastError()).isEqualTo("broker still unavailable");
		assertThat(updated.getNextRetryAt()).isNull();
	}

	@Test
	void eventOlderThanMaxAgeMarkedFailedWhileRetriesRemain() throws Exception {
		Transfer transfer = savedTransfer();
		OutboxEvent event = pendingOutboxEvent(
				transfer,
				OutboxEventType.TRANSFER_FAILED,
				Instant.now().minus(Duration.ofHours(25)));
		doThrow(new RuntimeException("broker unavailable"))
				.when(transferEventPublisher)
				.publish(any(), any(TransferEventOutboxPayload.class), any(String.class), any());

		walletOutboxWorker.processPendingTransferEvents();

		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("FAILED");
		assertThat(updated.getRetryCount()).isEqualTo(1);
		assertThat(updated.getNextRetryAt()).isNull();
	}

	@Test
	void staleProcessingEventIsRecoveredAndPublished() throws Exception {
		Transfer transfer = savedTransfer();
		OutboxEvent event = pendingOutboxEvent(transfer, OutboxEventType.TRANSFER_COMPLETED, Instant.now());
		event.markProcessing(Instant.now().minusSeconds(600));
		outboxEventRepository.saveAndFlush(event);

		walletOutboxWorker.processPendingTransferEvents();

		verify(transferEventPublisher).publish(
				eq(OutboxEventType.TRANSFER_COMPLETED),
				any(TransferEventOutboxPayload.class),
				eq("wallet-outbox-" + event.getId()),
				eq("trace-outbox"));
		OutboxEvent updated = outboxEventRepository.findById(event.getId()).orElseThrow();
		assertThat(updated.getStatus().name()).isEqualTo("PROCESSED");
		assertThat(updated.getRetryCount()).isEqualTo(1);
		assertThat(updated.getLastError()).isNull();
		assertThat(updated.getNextRetryAt()).isNull();
	}

	private Transfer savedTransfer() {
		Instant now = Instant.now();
		Wallet sender = walletRepository.save(Wallet.create(
				UUID.randomUUID(), UUID.randomUUID(), 100_000L, WalletStatus.ACTIVE, now, now));
		Wallet receiver = walletRepository.save(Wallet.create(
				UUID.randomUUID(), UUID.randomUUID(), 0L, WalletStatus.ACTIVE, now, now));
		return transferRepository.save(Transfer.create(
				UUID.randomUUID(), sender, receiver, 40_000L, TransferStatus.SUCCESS, null, now));
	}

	private OutboxEvent pendingOutboxEvent(Transfer transfer, OutboxEventType eventType, Instant createdAt) {
		TransferEventOutboxPayload payload = new TransferEventOutboxPayload(
				transfer.getId(),
				transfer.getSenderWallet().getId(),
				transfer.getReceiverWallet().getId(),
				UUID.randomUUID(),
				transfer.getAmount(),
				transfer.getFailureReason());
		return outboxEventRepository.save(OutboxEvent.createPending(
				UUID.randomUUID(),
				eventType,
				transfer.getId(),
				writeJson(payload),
				"trace-outbox",
				createdAt));
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize test outbox payload", ex);
		}
	}
}
