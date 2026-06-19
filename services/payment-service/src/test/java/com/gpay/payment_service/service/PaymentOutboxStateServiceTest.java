package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.config.PaymentOutboxProperties;
import com.gpay.payment_service.constant.OutboxEventStatus;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.dto.ClaimedOutboxEvent;
import com.gpay.payment_service.dto.WalletCreditOutboxPayload;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.entity.TopupTransaction;
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

	@Test
	void claimReturnsNullWhenEventIsMissing() {
		PaymentOutboxStateService service = newService(outboxProperties(10, 60_000L));
		UUID eventId = UUID.randomUUID();
		when(outboxEventRepository.findLockedById(eventId)).thenReturn(Optional.empty());

		assertThat(service.claim(eventId)).isNull();
	}

	@Test
	void claimReturnsNullWhenEventIsNotPending() {
		PaymentOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markProcessing(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		assertThat(service.claim(event.getId())).isNull();
	}

	@Test
	void claimReturnsNullWhenRetryIsStillInTheFuture() {
		PaymentOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markFailedAttempt("retry-later", Instant.now().plus(Duration.ofMinutes(5)), Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		assertThat(service.claim(event.getId())).isNull();
	}

	@Test
	void claimMarksProcessingAndReturnsPayloadWithTraceId() throws Exception {
		PaymentOutboxStateService service = newService(outboxProperties(10, 60_000L));
		UUID aggregateId = UUID.randomUUID();
		OutboxEvent event = OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.CREDIT_WALLET_REQUESTED,
				aggregateId,
				"{}",
				Instant.now().minusSeconds(1));
		WalletCreditOutboxPayload payload = new WalletCreditOutboxPayload(UUID.randomUUID(), aggregateId, 75_000L);
		TopupTransaction transaction = TopupTransaction.createPending(
				aggregateId,
				UUID.randomUUID(),
				payload.walletId(),
				75_000L,
				"key",
				"trace-claim",
				Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));
		when(objectMapper.readValue("{}", WalletCreditOutboxPayload.class)).thenReturn(payload);
		when(topupTransactionRepository.findById(aggregateId)).thenReturn(Optional.of(transaction));

		ClaimedOutboxEvent claimed = service.claim(event.getId());

		assertThat(claimed).isNotNull();
		assertThat(claimed.eventId()).isEqualTo(event.getId());
		assertThat(claimed.payload()).isEqualTo(payload);
		assertThat(claimed.traceId()).isEqualTo("trace-claim");
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
	}

	@Test
	void recoverStaleProcessingIgnoresEventThatIsNoLongerStale() {
		PaymentOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markProcessing(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recoverStaleProcessing(event.getId(), Instant.now().minus(Duration.ofMinutes(10)));

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
	}

	@Test
	void recoverStaleProcessingRetriesEventWithinBudgetAndAge() {
		PaymentOutboxStateService service = newService(outboxProperties(10, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markProcessing(Instant.now().minus(Duration.ofMinutes(20)));
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recoverStaleProcessing(event.getId(), Instant.now().minus(Duration.ofMinutes(10)));

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getRetryCount()).isEqualTo(1);
	}

	@Test
	void recoverStaleProcessingMarksFailedWhenMaxAgeExceeded() {
		PaymentOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.CREDIT_WALLET_REQUESTED,
				UUID.randomUUID(),
				"{}",
				Instant.now().minus(Duration.ofDays(2)));
		event.markProcessing(Instant.now().minus(Duration.ofMinutes(20)));
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recoverStaleProcessing(event.getId(), Instant.now().minus(Duration.ofMinutes(10)));

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
	}

	@Test
	void recordFailedAttemptMarksFailedWhenNotRetryable() {
		PaymentOutboxStateService service = newService(outboxProperties(10, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recordFailedAttempt(event.getId(), "non-retryable", false);

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
	}

	@Test
	void recordFailedAttemptReschedulesWhenRetryable() {
		PaymentOutboxStateService service = newService(outboxProperties(10, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recordFailedAttempt(event.getId(), "retry-later", true);

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getRetryCount()).isEqualTo(1);
		assertThat(event.getNextRetryAt()).isAfter(Instant.now());
	}

	@Test
	void markProcessedSetsProcessedStatus() {
		PaymentOutboxStateService service = newService(outboxProperties(10, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markProcessing(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.markProcessed(event.getId());

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
	}

	@Test
	void claimWrapsInvalidPayloadAsIllegalState() throws Exception {
		PaymentOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now().minusSeconds(1));
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));
		lenient().when(objectMapper.readValue("{}", WalletCreditOutboxPayload.class))
				.thenThrow(new JsonProcessingException("bad") {});

		assertThatThrownBy(() -> service.claim(event.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Outbox payload is invalid");
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
