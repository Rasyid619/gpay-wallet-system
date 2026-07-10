package com.gpay.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gpay.notification_service.config.NotificationRetryProperties;
import com.gpay.notification_service.constant.NotificationStatus;
import com.gpay.notification_service.constant.NotificationType;
import com.gpay.notification_service.dto.NotificationRequest;
import com.gpay.notification_service.entity.NotificationAttempt;
import com.gpay.notification_service.exception.EmailDeliveryException;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import com.gpay.notification_service.repository.NotificationAttemptRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for idempotent notification delivery, covering duplicate skip,
 * successful send, retryable failure counting, exhausted retry budget, and
 * immediate failure on non-retryable errors.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final String RECIPIENT = "recipient@example.com";

	@Mock
	private EmailService emailService;

	@Mock
	private EmailTemplateService emailTemplateService;

	@Mock
	private NotificationAttemptRepository notificationAttemptRepository;

	@Mock
	private UserEmailClient userEmailClient;

	private NotificationService notificationService;

	@BeforeEach
	void setUp() {
		notificationService = new NotificationService(
				emailService,
				emailTemplateService,
				notificationAttemptRepository,
				new NotificationRetryProperties(3),
				userEmailClient);
	}

	private NotificationRequest request(UUID eventId) {
		return new NotificationRequest(
				eventId,
				NotificationType.TRANSFER_COMPLETED,
				UUID.randomUUID(),
				Map.of("amount", 10_000L));
	}

	private NotificationAttempt attempt(UUID eventId) {
		return NotificationAttempt.createPending(
				UUID.randomUUID(),
				eventId,
				NotificationType.TRANSFER_COMPLETED,
				RECIPIENT,
				"trace",
				Instant.now());
	}

	@Test
	void skipsDeliveryWhenEventWasAlreadySent() {
		UUID eventId = UUID.randomUUID();
		NotificationAttempt sentAttempt = attempt(eventId);
		sentAttempt.markSent(Instant.now());
		when(notificationAttemptRepository.findByEventId(eventId)).thenReturn(Optional.of(sentAttempt));

		notificationService.deliver(request(eventId));

		verifyNoInteractions(emailService, emailTemplateService, userEmailClient);
		verify(notificationAttemptRepository, never()).save(any());
	}

	@Test
	void sendsEmailAndMarksAttemptSentOnFirstDelivery() {
		UUID eventId = UUID.randomUUID();
		when(notificationAttemptRepository.findByEventId(eventId)).thenReturn(Optional.empty());
		when(userEmailClient.fetchEmail(any())).thenReturn(RECIPIENT);
		when(notificationAttemptRepository.save(any(NotificationAttempt.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(emailTemplateService.render(any(), anyString(), anyMap())).thenReturn("<html>body</html>");

		notificationService.deliver(request(eventId));

		verify(emailService).send(RECIPIENT, NotificationType.TRANSFER_COMPLETED.getSubject(), "<html>body</html>");
		ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
		verify(notificationAttemptRepository, times(2)).save(captor.capture());
		NotificationAttempt saved = captor.getValue();
		assertThat(saved.getEventId()).isEqualTo(eventId);
		assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(saved.getSentAt()).isNotNull();
		assertThat(saved.getRetryCount()).isZero();
	}

	@Test
	void retryableFailureIncrementsRetryCountAndRethrows() {
		UUID eventId = UUID.randomUUID();
		NotificationAttempt existing = attempt(eventId);
		when(notificationAttemptRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
		when(emailTemplateService.render(any(), anyString(), anyMap())).thenReturn("<html>body</html>");
		doThrow(new EmailDeliveryException("smtp down", new RuntimeException()))
				.when(emailService).send(anyString(), anyString(), anyString());

		assertThatThrownBy(() -> notificationService.deliver(request(eventId)))
				.isInstanceOf(EmailDeliveryException.class);

		assertThat(existing.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(existing.getRetryCount()).isEqualTo(1);
		assertThat(existing.getSentAt()).isNull();
		verify(notificationAttemptRepository).save(existing);
		verifyNoInteractions(userEmailClient);
	}

	@Test
	void retryableFailureAtExhaustedBudgetMarksAttemptFailed() {
		UUID eventId = UUID.randomUUID();
		NotificationAttempt existing = attempt(eventId);
		existing.markFailedAttempt(Instant.now());
		existing.markFailedAttempt(Instant.now());
		when(notificationAttemptRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
		when(emailTemplateService.render(any(), anyString(), anyMap())).thenReturn("<html>body</html>");
		doThrow(new EmailDeliveryException("smtp still down", new RuntimeException()))
				.when(emailService).send(anyString(), anyString(), anyString());

		assertThatThrownBy(() -> notificationService.deliver(request(eventId)))
				.isInstanceOf(EmailDeliveryException.class);

		assertThat(existing.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(existing.getRetryCount()).isEqualTo(3);
	}

	@Test
	void nonRetryableFailureMarksAttemptFailedImmediately() {
		UUID eventId = UUID.randomUUID();
		NotificationAttempt existing = attempt(eventId);
		when(notificationAttemptRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
		when(emailTemplateService.render(any(), anyString(), anyMap())).thenReturn("<html>body</html>");
		doThrow(new NonRetryableNotificationException("bad address"))
				.when(emailService).send(anyString(), anyString(), anyString());

		assertThatThrownBy(() -> notificationService.deliver(request(eventId)))
				.isInstanceOf(NonRetryableNotificationException.class);

		assertThat(existing.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(existing.getRetryCount()).isEqualTo(1);
	}

	@Test
	void reusesStoredRecipientWithoutLookupOnRedelivery() {
		UUID eventId = UUID.randomUUID();
		NotificationAttempt existing = attempt(eventId);
		when(notificationAttemptRepository.findByEventId(eventId)).thenReturn(Optional.of(existing));
		when(emailTemplateService.render(any(), anyString(), anyMap())).thenReturn("<html>body</html>");

		notificationService.deliver(request(eventId));

		verifyNoInteractions(userEmailClient);
		verify(emailService).send(RECIPIENT, NotificationType.TRANSFER_COMPLETED.getSubject(), "<html>body</html>");
		assertThat(existing.getStatus()).isEqualTo(NotificationStatus.SENT);
	}
}
