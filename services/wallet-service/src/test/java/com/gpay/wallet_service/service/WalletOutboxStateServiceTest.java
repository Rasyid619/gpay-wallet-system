package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.wallet_service.config.WalletOutboxProperties;
import com.gpay.wallet_service.constant.OutboxEventStatus;
import com.gpay.wallet_service.constant.OutboxEventType;
import com.gpay.wallet_service.dto.ClaimedTransferOutboxEvent;
import com.gpay.wallet_service.dto.TransferEventOutboxPayload;
import com.gpay.wallet_service.entity.OutboxEvent;
import com.gpay.wallet_service.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Isolated unit tests for the wallet outbox state transitions, exercising the
 * claim guards and the max-age / attempt-budget / non-retryable FAILED branches
 * without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class WalletOutboxStateServiceTest {

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private OutboxEventRepository outboxEventRepository;

	private WalletOutboxStateService newService(WalletOutboxProperties properties) {
		return new WalletOutboxStateService(objectMapper, outboxEventRepository, properties);
	}

	private WalletOutboxProperties outboxProperties(int maxAttempts, long maxAgeMs) {
		return new WalletOutboxProperties(
				60_000L,
				maxAttempts,
				maxAgeMs,
				300_000L,
				10,
				3_600_000L,
				3_600_000L);
	}

	private OutboxEvent pendingEvent(Instant createdAt) {
		return OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.TRANSFER_COMPLETED,
				UUID.randomUUID(),
				"{}",
				"trace-state",
				createdAt);
	}

	@Test
	void claimReturnsNullWhenEventIsMissing() {
		WalletOutboxStateService service = newService(outboxProperties(10, 60_000L));
		UUID eventId = UUID.randomUUID();
		when(outboxEventRepository.findLockedById(eventId)).thenReturn(Optional.empty());

		assertThat(service.claim(eventId)).isNull();
	}

	@Test
	void claimReturnsNullWhenEventIsNotPending() {
		WalletOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markProcessing(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		assertThat(service.claim(event.getId())).isNull();
	}

	@Test
	void claimReturnsNullWhenRetryIsStillInTheFuture() {
		WalletOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markFailedAttempt("retry-later", Instant.now().plus(Duration.ofMinutes(5)), Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		assertThat(service.claim(event.getId())).isNull();
	}

	@Test
	void claimMarksProcessingAndReturnsPayloadWithStoredTraceId() throws Exception {
		WalletOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now().minusSeconds(1));
		TransferEventOutboxPayload payload = new TransferEventOutboxPayload(
				event.getAggregateId(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				75_000L,
				null);
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));
		when(objectMapper.readValue("{}", TransferEventOutboxPayload.class)).thenReturn(payload);

		ClaimedTransferOutboxEvent claimed = service.claim(event.getId());

		assertThat(claimed).isNotNull();
		assertThat(claimed.eventId()).isEqualTo(event.getId());
		assertThat(claimed.eventType()).isEqualTo(OutboxEventType.TRANSFER_COMPLETED);
		assertThat(claimed.payload()).isEqualTo(payload);
		assertThat(claimed.traceId()).isEqualTo("trace-state");
		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
	}

	@Test
	void claimWrapsInvalidPayloadAsIllegalState() throws Exception {
		WalletOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now().minusSeconds(1));
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));
		lenient().when(objectMapper.readValue("{}", TransferEventOutboxPayload.class))
				.thenThrow(new JsonProcessingException("bad") {});

		assertThatThrownBy(() -> service.claim(event.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Outbox payload is invalid");
	}

	@Test
	void recordFailedAttemptReschedulesWhenRetryable() {
		WalletOutboxStateService service = newService(outboxProperties(10, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recordFailedAttempt(event.getId(), "retry-later", true);

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getRetryCount()).isEqualTo(1);
		assertThat(event.getNextRetryAt()).isAfter(Instant.now());
	}

	@Test
	void recordFailedAttemptMarksFailedWhenNotRetryable() {
		WalletOutboxStateService service = newService(outboxProperties(10, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recordFailedAttempt(event.getId(), "non-retryable", false);

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
	}

	@Test
	void recordFailedAttemptMarksFailedWhenAttemptBudgetExhausted() {
		WalletOutboxStateService service = newService(outboxProperties(3, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		Instant past = Instant.now().minusSeconds(1);
		event.markFailedAttempt("attempt-1", past, past);
		event.markFailedAttempt("attempt-2", past, past);
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recordFailedAttempt(event.getId(), "broker still unavailable", true);

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
		assertThat(event.getRetryCount()).isEqualTo(3);
	}

	@Test
	void recordFailedAttemptMarksFailedWhenMaxAgeExceeded() {
		WalletOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now().minus(Duration.ofDays(2)));
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recordFailedAttempt(event.getId(), "broker unavailable", true);

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
		assertThat(event.getNextRetryAt()).isNull();
	}

	@Test
	void recoverStaleProcessingIgnoresEventThatIsNotProcessing() {
		WalletOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recoverStaleProcessing(event.getId(), Instant.now());

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
	}

	@Test
	void recoverStaleProcessingIgnoresEventThatIsNoLongerStale() {
		WalletOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markProcessing(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recoverStaleProcessing(event.getId(), Instant.now().minus(Duration.ofMinutes(10)));

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
	}

	@Test
	void recoverStaleProcessingRetriesEventWithinBudgetAndAge() {
		WalletOutboxStateService service = newService(outboxProperties(10, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markProcessing(Instant.now().minus(Duration.ofMinutes(20)));
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recoverStaleProcessing(event.getId(), Instant.now().minus(Duration.ofMinutes(10)));

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(event.getRetryCount()).isEqualTo(1);
	}

	@Test
	void recoverStaleProcessingMarksFailedWhenAttemptBudgetExhausted() {
		WalletOutboxStateService service = newService(outboxProperties(3, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		Instant past = Instant.now().minusSeconds(1);
		event.markFailedAttempt("attempt-1", past, past);
		event.markFailedAttempt("attempt-2", past, past);
		event.markProcessing(Instant.now().minus(Duration.ofMinutes(20)));
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recoverStaleProcessing(event.getId(), Instant.now().minus(Duration.ofMinutes(10)));

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
		assertThat(event.getRetryCount()).isEqualTo(3);
	}

	@Test
	void recoverStaleProcessingMarksFailedWhenMaxAgeExceeded() {
		WalletOutboxStateService service = newService(outboxProperties(10, 60_000L));
		OutboxEvent event = pendingEvent(Instant.now().minus(Duration.ofDays(2)));
		event.markProcessing(Instant.now().minus(Duration.ofMinutes(20)));
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.recoverStaleProcessing(event.getId(), Instant.now().minus(Duration.ofMinutes(10)));

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
	}

	@Test
	void markProcessedSetsProcessedStatus() {
		WalletOutboxStateService service = newService(outboxProperties(10, 86_400_000L));
		OutboxEvent event = pendingEvent(Instant.now());
		event.markProcessing(Instant.now());
		when(outboxEventRepository.findLockedById(event.getId())).thenReturn(Optional.of(event));

		service.markProcessed(event.getId());

		assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
	}
}
