package com.gpay.notification_service.service;

import com.gpay.common.tracing.TraceIdContext;
import com.gpay.notification_service.config.NotificationRetryProperties;
import com.gpay.notification_service.constant.NotificationStatus;
import com.gpay.notification_service.dto.NotificationRequest;
import com.gpay.notification_service.entity.NotificationAttempt;
import com.gpay.notification_service.exception.EmailDeliveryException;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import com.gpay.notification_service.repository.NotificationAttemptRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Delivers one notification per event id, exactly once.
 *
 * <p>Redelivered events are skipped once an attempt is SENT. Transient send
 * failures increment the persisted retry count and rethrow so the Kafka error
 * handler retries; when the retry budget is exhausted or the failure is
 * non-retryable the attempt is marked FAILED before rethrowing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final EmailService emailService;
	private final EmailTemplateService emailTemplateService;
	private final NotificationAttemptRepository notificationAttemptRepository;
	private final NotificationRetryProperties retryProperties;
	private final UserEmailClient userEmailClient;

	/**
	 * Sends the email for one consumed event, idempotently by event id.
	 *
	 * @param request notification distilled from the consumed event
	 */
	public void deliver(NotificationRequest request) {
		NotificationAttempt existing = notificationAttemptRepository.findByEventId(request.eventId())
				.orElse(null);
		if (existing != null && existing.getStatus() == NotificationStatus.SENT) {
			log.info("Skipping duplicate notification eventId={}", request.eventId());
			return;
		}

		NotificationAttempt attempt = existing != null ? existing : createPendingAttempt(request);
		String htmlBody = emailTemplateService.render(
				request.type(),
				attempt.getRecipientEmail(),
				request.templateVariables());
		sendAndRecordOutcome(attempt, htmlBody);
	}

	private NotificationAttempt createPendingAttempt(NotificationRequest request) {
		String recipientEmail = userEmailClient.fetchEmail(request.recipientUserId());
		return notificationAttemptRepository.save(NotificationAttempt.createPending(
				UUID.randomUUID(),
				request.eventId(),
				request.type(),
				recipientEmail,
				TraceIdContext.getTraceId(),
				Instant.now()));
	}

	private void sendAndRecordOutcome(NotificationAttempt attempt, String htmlBody) {
		try {
			emailService.send(attempt.getRecipientEmail(), attempt.getNotificationType().getSubject(), htmlBody);
		} catch (EmailDeliveryException ex) {
			recordRetryableFailure(attempt);
			throw ex;
		} catch (NonRetryableNotificationException ex) {
			attempt.markFailed(Instant.now());
			notificationAttemptRepository.save(attempt);
			throw ex;
		}

		attempt.markSent(Instant.now());
		notificationAttemptRepository.save(attempt);
		log.info(
				"Sent notification eventId={} type={} recipient={}",
				attempt.getEventId(),
				attempt.getNotificationType(),
				attempt.getRecipientEmail());
	}

	private void recordRetryableFailure(NotificationAttempt attempt) {
		Instant now = Instant.now();
		boolean isBudgetExhausted = attempt.getRetryCount() + 1 >= retryProperties.maxAttempts();
		if (isBudgetExhausted) {
			attempt.markFailed(now);
			notificationAttemptRepository.save(attempt);
			return;
		}

		attempt.markFailedAttempt(now);
		notificationAttemptRepository.save(attempt);
	}
}
