package com.gpay.payment_service.service;

import com.gpay.payment_service.config.PaymentOutboxProperties;
import com.gpay.payment_service.constant.OutboxEventStatus;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.dto.ClaimedOutboxEvent;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOutboxWorker {

	private final OutboxEventRepository outboxEventRepository;
	private final PaymentOutboxStateService paymentOutboxStateService;
	private final PaymentOutboxProperties properties;
	private final WalletCreditClient walletCreditClient;

	@Scheduled(
			fixedDelayString = "${payment.outbox.worker-fixed-delay-ms}",
			initialDelayString = "${payment.outbox.worker-initial-delay-ms}")
	public void processPendingWalletCredits() {
		List<UUID> dueEventIds = findDueEventIds();
		for (UUID eventId : dueEventIds) {
			processEvent(eventId);
		}
	}

	private List<UUID> findDueEventIds() {
		return outboxEventRepository.findDueEvents(
						OutboxEventType.CREDIT_WALLET_REQUESTED,
						OutboxEventStatus.PENDING,
						Instant.now(),
						PageRequest.of(0, properties.batchSize()))
				.stream()
				.map(OutboxEvent::getId)
				.toList();
	}

	private void processEvent(UUID eventId) {
		ClaimedOutboxEvent claimedEvent = paymentOutboxStateService.claim(eventId);
		if (claimedEvent == null) {
			return;
		}

		try {
			walletCreditClient.creditWallet(
					claimedEvent.payload(),
					idempotencyKey(claimedEvent.eventId()),
					claimedEvent.traceId());
			paymentOutboxStateService.markProcessed(claimedEvent.eventId());
		} catch (RuntimeException ex) {
			log.warn("Wallet credit outbox delivery failed for eventId={}", eventId, ex);
			paymentOutboxStateService.markFailedAttempt(
					claimedEvent.eventId(),
					safeError(ex.getMessage()),
					properties.retryDelayMs());
		}
	}

	private String idempotencyKey(UUID eventId) {
		return "payment-outbox-" + eventId;
	}

	private String safeError(String error) {
		if (error == null || error.isBlank()) {
			return "Wallet credit delivery failed";
		}
		return error.length() > 500 ? error.substring(0, 500) : error;
	}
}
