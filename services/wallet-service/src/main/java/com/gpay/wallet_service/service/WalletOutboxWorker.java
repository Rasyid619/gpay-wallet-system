package com.gpay.wallet_service.service;

import com.gpay.wallet_service.config.WalletOutboxProperties;
import com.gpay.wallet_service.constant.OutboxEventStatus;
import com.gpay.wallet_service.dto.ClaimedTransferOutboxEvent;
import com.gpay.wallet_service.entity.OutboxEvent;
import com.gpay.wallet_service.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Drains PENDING transfer-result outbox events by publishing them to Kafka and
 * only marking the row PROCESSED once the broker acknowledges the publish.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletOutboxWorker {

	private final OutboxEventRepository outboxEventRepository;
	private final TransferEventPublisher transferEventPublisher;
	private final WalletOutboxProperties properties;
	private final WalletOutboxStateService walletOutboxStateService;

	@Scheduled(
			fixedDelayString = "${wallet.outbox.worker-fixed-delay-ms}",
			initialDelayString = "${wallet.outbox.worker-initial-delay-ms}")
	public void processPendingTransferEvents() {
		recoverStaleProcessingEvents();
		List<UUID> dueEventIds = findDueEventIds();
		for (UUID eventId : dueEventIds) {
			processEvent(eventId);
		}
	}

	private void recoverStaleProcessingEvents() {
		Instant staleBefore = Instant.now().minusMillis(properties.processingTimeoutMs());
		List<UUID> staleEventIds = outboxEventRepository.findStaleProcessingEvents(
						OutboxEventStatus.PROCESSING,
						staleBefore,
						PageRequest.of(0, properties.batchSize()))
				.stream()
				.map(OutboxEvent::getId)
				.toList();
		for (UUID eventId : staleEventIds) {
			walletOutboxStateService.recoverStaleProcessing(eventId, staleBefore);
		}
	}

	private List<UUID> findDueEventIds() {
		return outboxEventRepository.findDueEvents(
						OutboxEventStatus.PENDING,
						Instant.now(),
						PageRequest.of(0, properties.batchSize()))
				.stream()
				.map(OutboxEvent::getId)
				.toList();
	}

	private void processEvent(UUID eventId) {
		ClaimedTransferOutboxEvent claimedEvent = walletOutboxStateService.claim(eventId);
		if (claimedEvent == null) {
			return;
		}

		try {
			transferEventPublisher.publish(
					claimedEvent.eventType(),
					claimedEvent.payload(),
					idempotencyKey(claimedEvent.eventId()),
					claimedEvent.traceId());
			walletOutboxStateService.markProcessed(claimedEvent.eventId());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			recordRetryableFailure(eventId, claimedEvent.eventId(), ex);
		} catch (ExecutionException | RuntimeException ex) {
			recordRetryableFailure(eventId, claimedEvent.eventId(), ex);
		}
	}

	private void recordRetryableFailure(UUID eventId, UUID claimedEventId, Exception ex) {
		log.warn("Transfer event publish failed for eventId={}", eventId, ex);
		walletOutboxStateService.recordFailedAttempt(claimedEventId, safeError(ex.getMessage()), true);
	}

	private String idempotencyKey(UUID eventId) {
		return "wallet-outbox-" + eventId;
	}

	private String safeError(String error) {
		if (error == null || error.isBlank()) {
			return "Transfer event delivery failed";
		}
		return error.length() > 500 ? error.substring(0, 500) : error;
	}
}
