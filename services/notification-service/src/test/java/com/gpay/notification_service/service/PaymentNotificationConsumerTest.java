package com.gpay.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.gpay.notification_service.constant.NotificationType;
import com.gpay.notification_service.dto.NotificationRequest;
import com.gpay.notification_service.dto.PaymentTopupEventPayload;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the top-up event consumer, covering delegation with the
 * parsed event id per topic kind and the malformed-record guard.
 */
@ExtendWith(MockitoExtension.class)
class PaymentNotificationConsumerTest {

	@Mock
	private NotificationService notificationService;

	private ConsumerRecord<String, PaymentTopupEventPayload> record(
			PaymentTopupEventPayload payload,
			String idempotencyKey) {
		ConsumerRecord<String, PaymentTopupEventPayload> record = new ConsumerRecord<>(
				"payment.topup.succeeded", 0, 0L, "key", payload);
		if (idempotencyKey != null) {
			record.headers().add("Idempotency-Key", idempotencyKey.getBytes(StandardCharsets.UTF_8));
		}
		return record;
	}

	private PaymentTopupEventPayload payload(UUID userId, String failureReason) {
		return new PaymentTopupEventPayload(
				UUID.randomUUID(),
				userId,
				UUID.randomUUID(),
				75_000L,
				failureReason);
	}

	@Test
	void delegatesSucceededEventWithParsedEventId() {
		PaymentNotificationConsumer consumer = new PaymentNotificationConsumer(notificationService);
		UUID eventId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		consumer.onTopupSucceeded(record(payload(userId, null), "payment-outbox-" + eventId));

		ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
		verify(notificationService).deliver(captor.capture());
		NotificationRequest request = captor.getValue();
		assertThat(request.eventId()).isEqualTo(eventId);
		assertThat(request.type()).isEqualTo(NotificationType.TOPUP_SUCCEEDED);
		assertThat(request.recipientUserId()).isEqualTo(userId);
	}

	@Test
	void delegatesFailedEventWithFailureReasonVariable() {
		PaymentNotificationConsumer consumer = new PaymentNotificationConsumer(notificationService);

		consumer.onTopupFailed(record(
				payload(UUID.randomUUID(), "Gateway reported payment failure"),
				"payment-outbox-" + UUID.randomUUID()));

		ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
		verify(notificationService).deliver(captor.capture());
		assertThat(captor.getValue().type()).isEqualTo(NotificationType.TOPUP_FAILED);
		assertThat(captor.getValue().templateVariables())
				.containsEntry("failure_reason", "Gateway reported payment failure");
	}

	@Test
	void rejectsRecordWithMissingPayload() {
		PaymentNotificationConsumer consumer = new PaymentNotificationConsumer(notificationService);

		assertThatThrownBy(() -> consumer.onTopupSucceeded(record(null, "payment-outbox-" + UUID.randomUUID())))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void rejectsRecordWithMissingUserId() {
		PaymentNotificationConsumer consumer = new PaymentNotificationConsumer(notificationService);
		PaymentTopupEventPayload payload = new PaymentTopupEventPayload(
				UUID.randomUUID(), null, UUID.randomUUID(), 75_000L, null);

		assertThatThrownBy(() -> consumer.onTopupSucceeded(record(payload, "payment-outbox-" + UUID.randomUUID())))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void rejectsRecordWithMissingAmount() {
		PaymentNotificationConsumer consumer = new PaymentNotificationConsumer(notificationService);
		PaymentTopupEventPayload payload = new PaymentTopupEventPayload(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);

		assertThatThrownBy(() -> consumer.onTopupSucceeded(record(payload, "payment-outbox-" + UUID.randomUUID())))
				.isInstanceOf(NonRetryableNotificationException.class);
	}
}
