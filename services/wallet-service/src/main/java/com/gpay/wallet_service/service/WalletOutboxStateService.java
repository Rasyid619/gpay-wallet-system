package com.gpay.wallet_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.wallet_service.config.WalletOutboxProperties;
import com.gpay.wallet_service.constant.OutboxEventStatus;
import com.gpay.wallet_service.dto.ClaimedTransferOutboxEvent;
import com.gpay.wallet_service.dto.TransferEventOutboxPayload;
import com.gpay.wallet_service.entity.OutboxEvent;
import com.gpay.wallet_service.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Transactional state changes for wallet outbox events during delivery. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletOutboxStateService {

	private static final String PROCESSING_TIMEOUT_ERROR = "Outbox processing timed out before completion";

	private final ObjectMapper objectMapper;
	private final OutboxEventRepository outboxEventRepository;
	private final WalletOutboxProperties properties;

	/**
	 * Claims a due pending event by locking the row and marking it PROCESSING.
	 *
	 * @param eventId outbox event identifier
	 * @return the claimed event, or {@code null} when it is no longer claimable
	 */
	@Transactional
	public ClaimedTransferOutboxEvent claim(UUID eventId) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElse(null);
		if (event == null || event.getStatus() != OutboxEventStatus.PENDING) {
			return null;
		}
		Instant now = Instant.now();
		if (event.getNextRetryAt() != null && event.getNextRetryAt().isAfter(now)) {
			return null;
		}

		event.markProcessing(now);
		outboxEventRepository.save(event);
		TransferEventOutboxPayload payload = readPayload(event.getPayload());
		return new ClaimedTransferOutboxEvent(event.getId(), event.getEventType(), payload, event.getTraceId());
	}

	@Transactional
	public void markProcessed(UUID eventId) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElseThrow();
		event.markProcessed(Instant.now());
		outboxEventRepository.save(event);
	}

	/**
	 * Records a delivery failure, retrying with backoff until the attempt budget
	 * is exhausted, the event has outlived its max age, or the failure is
	 * non-retryable, then moving to FAILED.
	 *
	 * @param eventId   outbox event identifier
	 * @param error     short failure description retained for diagnostics
	 * @param retryable whether the failure may succeed on a later attempt
	 */
	@Transactional
	public void recordFailedAttempt(UUID eventId, String error, boolean retryable) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElseThrow();
		Instant now = Instant.now();
		if (!retryable || isAttemptBudgetExhausted(event) || isMaxAgeExceeded(event, now)) {
			markFailed(event, error, retryable, now);
			return;
		}
		event.markFailedAttempt(error, now.plusMillis(properties.retryDelayMs()), now);
		outboxEventRepository.save(event);
	}

	@Transactional
	public void recoverStaleProcessing(UUID eventId, Instant staleBefore) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElseThrow();
		if (event.getStatus() != OutboxEventStatus.PROCESSING || event.getUpdatedAt().isAfter(staleBefore)) {
			return;
		}
		Instant now = Instant.now();
		if (isAttemptBudgetExhausted(event) || isMaxAgeExceeded(event, now)) {
			markFailed(event, PROCESSING_TIMEOUT_ERROR, true, now);
			return;
		}
		event.markFailedAttempt(PROCESSING_TIMEOUT_ERROR, now, now);
		outboxEventRepository.save(event);
	}

	private boolean isAttemptBudgetExhausted(OutboxEvent event) {
		return event.getRetryCount() + 1 >= properties.maxAttempts();
	}

	private boolean isMaxAgeExceeded(OutboxEvent event, Instant now) {
		return !event.getCreatedAt().plusMillis(properties.maxAgeMs()).isAfter(now);
	}

	private void markFailed(OutboxEvent event, String error, boolean retryable, Instant now) {
		event.markFailed(error, now);
		outboxEventRepository.save(event);
		log.warn(
				"Outbox event moved to FAILED eventId={} aggregateId={} retryCount={} retryable={} lastError={}",
				event.getId(),
				event.getAggregateId(),
				event.getRetryCount(),
				retryable,
				error);
	}

	private TransferEventOutboxPayload readPayload(String payload) {
		try {
			return objectMapper.readValue(payload, TransferEventOutboxPayload.class);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Outbox payload is invalid", ex);
		}
	}
}
