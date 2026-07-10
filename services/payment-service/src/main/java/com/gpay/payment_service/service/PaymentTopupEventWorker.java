package com.gpay.payment_service.service;

import com.gpay.payment_service.config.PaymentOutboxProperties;
import com.gpay.payment_service.constant.OutboxEventStatus;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.dto.ClaimedTopupOutboxEvent;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Drains PENDING top-up result outbox events by publishing them to Kafka and
 * only marking the row PROCESSED once the broker acknowledges the publish.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTopupEventWorker {

	private static final List<OutboxEventType> TOPUP_EVENT_TYPES = List.of(
			OutboxEventType.TOPUP_SUCCEEDED,
			OutboxEventType.TOPUP_FAILED);

	private final OutboxEventRepository outboxEventRepository;
	private final PaymentOutboxProperties properties;
	private final PaymentOutboxStateService paymentOutboxStateService;
	private final TopupEventPublisher topupEventPublisher;

	@Scheduled(
			fixedDelayString = "${payment.outbox.worker-fixed-delay-ms}",
			initialDelayString = "${payment.outbox.worker-initial-delay-ms}")
	public void processPendingTopupEvents() {
		recoverStaleProcessingEvents();
		List<UUID> dueEventIds = findDueEventIds();
		for (UUID eventId : dueEventIds) {
			processEvent(eventId);
		}
	}

	private void recoverStaleProcessingEvents() {
		Instant staleBefore = Instant.now().minusMillis(properties.processingTimeoutMs());
		List<UUID> staleEventIds = TOPUP_EVENT_TYPES.stream()
				.flatMap(eventType -> outboxEventRepository.findStaleProcessingEvents(
								eventType,
								OutboxEventStatus.PROCESSING,
								staleBefore,
								PageRequest.of(0, properties.batchSize()))
						.stream())
				.map(OutboxEvent::getId)
				.toList();
		for (UUID eventId : staleEventIds) {
			paymentOutboxStateService.recoverStaleProcessing(eventId, staleBefore);
		}
	}

	private List<UUID> findDueEventIds() {
		Instant now = Instant.now();
		return TOPUP_EVENT_TYPES.stream()
				.flatMap(eventType -> findDueEventIds(eventType, now))
				.toList();
	}

	private Stream<UUID> findDueEventIds(OutboxEventType eventType, Instant now) {
		return outboxEventRepository.findDueEvents(
						eventType,
						OutboxEventStatus.PENDING,
						now,
						PageRequest.of(0, properties.batchSize()))
				.stream()
				.map(OutboxEvent::getId);
	}

	private void processEvent(UUID eventId) {
		ClaimedTopupOutboxEvent claimedEvent = paymentOutboxStateService.claimTopupEvent(eventId);
		if (claimedEvent == null) {
			return;
		}

		try {
			topupEventPublisher.publish(
					claimedEvent.eventType(),
					claimedEvent.payload(),
					idempotencyKey(claimedEvent.eventId()),
					claimedEvent.traceId());
			paymentOutboxStateService.markProcessed(claimedEvent.eventId());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			recordRetryableFailure(eventId, claimedEvent.eventId(), ex);
		} catch (ExecutionException | RuntimeException ex) {
			recordRetryableFailure(eventId, claimedEvent.eventId(), ex);
		}
	}

	private void recordRetryableFailure(UUID eventId, UUID claimedEventId, Exception ex) {
		log.warn("Top-up event publish failed for eventId={}", eventId, ex);
		paymentOutboxStateService.recordFailedAttempt(claimedEventId, safeError(ex.getMessage()), true);
	}

	private String idempotencyKey(UUID eventId) {
		return "payment-outbox-" + eventId;
	}

	private String safeError(String error) {
		if (error == null || error.isBlank()) {
			return "Top-up event delivery failed";
		}
		return error.length() > 500 ? error.substring(0, 500) : error;
	}
}
