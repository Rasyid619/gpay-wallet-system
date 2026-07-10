package com.gpay.notification_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gpay.notification_service.constant.NotificationStatus;
import com.gpay.notification_service.constant.NotificationType;
import com.gpay.notification_service.entity.NotificationAttempt;
import com.gpay.notification_service.repository.NotificationAttemptRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/*
 * Integration tests for notification attempt persistence against the Flyway-migrated
 * schema, covering the full status lifecycle roundtrip, event-id dedup lookup, and the
 * unique event_id constraint that anchors idempotent delivery.
 */
class NotificationAttemptPersistenceIntegrationTest extends AbstractIntegrationTest {

	private final NotificationAttemptRepository notificationAttemptRepository;

	@Autowired
	NotificationAttemptPersistenceIntegrationTest(NotificationAttemptRepository notificationAttemptRepository) {
		this.notificationAttemptRepository = notificationAttemptRepository;
	}

	private NotificationAttempt pendingAttempt(UUID eventId) {
		return NotificationAttempt.createPending(
				UUID.randomUUID(),
				eventId,
				NotificationType.TRANSFER_COMPLETED,
				"recipient@example.com",
				"trace-persist",
				Instant.now());
	}

	@Test
	void persistsAndReloadsAttemptThroughSentLifecycle() {
		UUID eventId = UUID.randomUUID();
		NotificationAttempt attempt = notificationAttemptRepository.save(pendingAttempt(eventId));

		attempt.markSent(Instant.now());
		notificationAttemptRepository.save(attempt);

		NotificationAttempt reloaded = notificationAttemptRepository.findByEventId(eventId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(reloaded.getNotificationType()).isEqualTo(NotificationType.TRANSFER_COMPLETED);
		assertThat(reloaded.getRecipientEmail()).isEqualTo("recipient@example.com");
		assertThat(reloaded.getTraceId()).isEqualTo("trace-persist");
		assertThat(reloaded.getSentAt()).isNotNull();
		assertThat(reloaded.getRetryCount()).isZero();
	}

	@Test
	void persistsReceiverSideTransferReceivedAttempt() {
		UUID eventId = UUID.randomUUID();
		notificationAttemptRepository.save(NotificationAttempt.createPending(
				UUID.randomUUID(),
				eventId,
				NotificationType.TRANSFER_RECEIVED,
				"receiver@example.com",
				"trace-received",
				Instant.now()));

		NotificationAttempt reloaded = notificationAttemptRepository.findByEventId(eventId).orElseThrow();
		assertThat(reloaded.getNotificationType()).isEqualTo(NotificationType.TRANSFER_RECEIVED);
		assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.PENDING);
	}

	@Test
	void persistsFailedAttemptWithRetryMetadata() {
		UUID eventId = UUID.randomUUID();
		NotificationAttempt attempt = notificationAttemptRepository.save(pendingAttempt(eventId));

		attempt.markFailedAttempt(Instant.now());
		attempt.markFailed(Instant.now());
		notificationAttemptRepository.save(attempt);

		NotificationAttempt reloaded = notificationAttemptRepository.findByEventId(eventId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(reloaded.getRetryCount()).isEqualTo(2);
		assertThat(reloaded.getSentAt()).isNull();
	}

	@Test
	void rejectsDuplicateEventIdThroughUniqueConstraint() {
		UUID eventId = UUID.randomUUID();
		notificationAttemptRepository.saveAndFlush(pendingAttempt(eventId));

		assertThatThrownBy(() -> notificationAttemptRepository.saveAndFlush(pendingAttempt(eventId)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void returnsEmptyWhenNoAttemptExistsForEventId() {
		assertThat(notificationAttemptRepository.findByEventId(UUID.randomUUID())).isEmpty();
	}
}
