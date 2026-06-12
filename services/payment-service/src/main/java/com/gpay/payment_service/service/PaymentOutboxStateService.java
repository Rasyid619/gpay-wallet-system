package com.gpay.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.config.PaymentOutboxProperties;
import com.gpay.payment_service.constant.OutboxEventStatus;
import com.gpay.payment_service.dto.ClaimedOutboxEvent;
import com.gpay.payment_service.dto.WalletCreditOutboxPayload;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.repository.OutboxEventRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxStateService {

	private static final String PROCESSING_TIMEOUT_ERROR = "Outbox processing timed out before completion";

	private final ObjectMapper objectMapper;
	private final OutboxEventRepository outboxEventRepository;
	private final PaymentOutboxProperties properties;
	private final TopupTransactionRepository topupTransactionRepository;

	@Transactional
	public ClaimedOutboxEvent claim(UUID eventId) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElse(null);
		if (event == null || event.getStatus() != OutboxEventStatus.PENDING) {
			return null;
		}
		Instant now = Instant.now();
		if (event.getNextRetryAt() != null && event.getNextRetryAt().isAfter(now)) {
			return null;
		}

		event.markProcessing(now);
		WalletCreditOutboxPayload payload = readPayload(event.getPayload());
		String traceId = topupTransactionRepository.findById(event.getAggregateId())
				.map(TopupTransaction::getTraceId)
				.orElse(null);
		return new ClaimedOutboxEvent(event.getId(), payload, traceId);
	}

	@Transactional
	public void markProcessed(UUID eventId) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElseThrow();
		event.markProcessed(Instant.now());
	}

	/**
	 * Records a delivery failure, retrying with backoff until the attempt budget
	 * is exhausted or the failure is non-retryable, then moving to FAILED.
	 *
	 * @param eventId   outbox event identifier
	 * @param error     short failure description retained for diagnostics
	 * @param retryable whether the failure may succeed on a later attempt
	 */
	@Transactional
	public void recordFailedAttempt(UUID eventId, String error, boolean retryable) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElseThrow();
		Instant now = Instant.now();
		if (!retryable || isAttemptBudgetExhausted(event)) {
			markFailed(event, error, retryable, now);
			return;
		}
		event.markFailedAttempt(error, now.plusMillis(properties.retryDelayMs()), now);
	}

	@Transactional
	public void recoverStaleProcessing(UUID eventId, Instant staleBefore) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElseThrow();
		if (event.getStatus() != OutboxEventStatus.PROCESSING || event.getUpdatedAt().isAfter(staleBefore)) {
			return;
		}
		Instant now = Instant.now();
		if (isAttemptBudgetExhausted(event)) {
			markFailed(event, PROCESSING_TIMEOUT_ERROR, true, now);
			return;
		}
		event.markFailedAttempt(PROCESSING_TIMEOUT_ERROR, now, now);
	}

	private boolean isAttemptBudgetExhausted(OutboxEvent event) {
		return event.getRetryCount() + 1 >= properties.maxAttempts();
	}

	private void markFailed(OutboxEvent event, String error, boolean retryable, Instant now) {
		event.markFailed(error, now);
		log.warn(
				"Outbox event moved to FAILED eventId={} aggregateId={} retryCount={} retryable={} lastError={}",
				event.getId(),
				event.getAggregateId(),
				event.getRetryCount(),
				retryable,
				error);
	}

	private WalletCreditOutboxPayload readPayload(String payload) {
		try {
			return objectMapper.readValue(payload, WalletCreditOutboxPayload.class);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Outbox payload is invalid", ex);
		}
	}
}
