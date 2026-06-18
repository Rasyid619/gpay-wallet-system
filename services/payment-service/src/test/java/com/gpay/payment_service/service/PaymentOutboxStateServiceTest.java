package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.config.PaymentOutboxProperties;
import com.gpay.payment_service.constant.OutboxEventStatus;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.repository.OutboxEventRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Isolated unit tests for the FAILED state transitions of the payment outbox,
 * exercising the max-age and attempt-budget branches directly through the
 * repository boundary without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOutboxStateServiceTest {

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private OutboxEventRepository outboxEventRepository;

	@Mock
	private TopupTransactionRepository topupTransactionRepository;

	@Test
	void recordFailedAttemptMarksFailedWhenMaxAgeExceeded() {
		PaymentOutboxProperties properties = outboxProperties(10, 60_000L);
		PaymentOutboxStateService service = newService(properties);
		OutboxEvent event = pendingEvent(Instant.now().minus(Duration.ofDays(2)));
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recordFailedAttempt(event.getId(), "broker unavailable", true);

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
		assertThat(event.getNextRetryAt()).isNull();
		assertThat(event.getLastError()).isEqualTo("broker unavailable");
	}

	@Test
	void recordFailedAttemptMarksFailedWhenAttemptBudgetExhausted() {
		PaymentOutboxProperties properties = outboxProperties(3, 60_000L);
		PaymentOutboxStateService service = newService(properties);
		OutboxEvent event = pendingEvent(Instant.now());
		Instant past = Instant.now().minusSeconds(1);
		event.markFailedAttempt("attempt-1", past, past);
		event.markFailedAttempt("attempt-2", past, past);
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recordFailedAttempt(event.getId(), "broker still unavailable", true);

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
		assertThat(event.getRetryCount()).isEqualTo(3);
		assertThat(event.getNextRetryAt()).isNull();
		assertThat(event.getLastError()).isEqualTo("broker still unavailable");
	}

	private PaymentOutboxStateService newService(PaymentOutboxProperties properties) {
		return new PaymentOutboxStateService(
				objectMapper,
				outboxEventRepository,
				properties,
				topupTransactionRepository);
	}

	private OutboxEvent pendingEvent(Instant createdAt) {
		return OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.CREDIT_WALLET_REQUESTED,
				UUID.randomUUID(),
				"{}",
				createdAt);
	}

	private PaymentOutboxProperties outboxProperties(int maxAttempts, long maxAgeMs) {
		return new PaymentOutboxProperties(
				60_000L,
				maxAttempts,
				maxAgeMs,
				300_000L,
				10,
				3_600_000L,
				3_600_000L);
	}
}
