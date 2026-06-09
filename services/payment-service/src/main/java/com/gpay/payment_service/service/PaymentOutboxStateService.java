package com.gpay.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentOutboxStateService {

	private final ObjectMapper objectMapper;
	private final OutboxEventRepository outboxEventRepository;
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

	@Transactional
	public void markFailedAttempt(UUID eventId, String error, Long retryDelayMs) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElseThrow();
		Instant now = Instant.now();
		event.markFailedAttempt(error, now.plusMillis(retryDelayMs), now);
	}

	@Transactional
	public void recoverStaleProcessing(UUID eventId, Instant staleBefore) {
		OutboxEvent event = outboxEventRepository.findLockedById(eventId).orElseThrow();
		if (event.getStatus() != OutboxEventStatus.PROCESSING || event.getUpdatedAt().isAfter(staleBefore)) {
			return;
		}
		Instant now = Instant.now();
		event.markFailedAttempt("Outbox processing timed out before completion", now, now);
	}

	private WalletCreditOutboxPayload readPayload(String payload) {
		try {
			return objectMapper.readValue(payload, WalletCreditOutboxPayload.class);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Outbox payload is invalid", ex);
		}
	}
}
