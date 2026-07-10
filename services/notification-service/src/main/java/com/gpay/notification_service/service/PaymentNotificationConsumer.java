package com.gpay.notification_service.service;

import com.gpay.common.tracing.KafkaTraceIdPropagation;
import com.gpay.notification_service.constant.NotificationType;
import com.gpay.notification_service.dto.NotificationRequest;
import com.gpay.notification_service.dto.PaymentTopupEventPayload;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes payment top-up result events and delivers the matching emails.
 *
 * <p>The trace id is restored from the record header into MDC before handling
 * and cleared afterwards. Duplicate deliveries are ignored by the event-id
 * dedup in {@link NotificationService}. Non-retryable failures bubble up so
 * the configured error handler routes them to the dead-letter topic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentNotificationConsumer {

	private final NotificationService notificationService;

	@KafkaListener(
			topics = "${notification.kafka.topup-succeeded-topic}",
			containerFactory = "topupEventListenerContainerFactory")
	public void onTopupSucceeded(ConsumerRecord<String, PaymentTopupEventPayload> record) {
		consume(record, NotificationType.TOPUP_SUCCEEDED);
	}

	@KafkaListener(
			topics = "${notification.kafka.topup-failed-topic}",
			containerFactory = "topupEventListenerContainerFactory")
	public void onTopupFailed(ConsumerRecord<String, PaymentTopupEventPayload> record) {
		consume(record, NotificationType.TOPUP_FAILED);
	}

	private void consume(ConsumerRecord<String, PaymentTopupEventPayload> record, NotificationType type) {
		KafkaTraceIdPropagation.restoreTraceId(record.headers());
		try {
			notificationService.deliver(toRequest(record, type));
		} finally {
			KafkaTraceIdPropagation.clearTraceId();
		}
	}

	private NotificationRequest toRequest(
			ConsumerRecord<String, PaymentTopupEventPayload> record,
			NotificationType type) {
		PaymentTopupEventPayload payload = record.value();
		if (payload == null || payload.userId() == null || payload.amount() == null) {
			throw new NonRetryableNotificationException("Top-up event payload is missing or incomplete");
		}

		Map<String, Object> variables = new HashMap<>();
		variables.put("transaction_id", payload.paymentTransactionId());
		variables.put("amount", payload.amount());
		variables.put("failure_reason", payload.failureReason());
		return new NotificationRequest(
				NotificationEventIds.readEventId(record.headers()),
				type,
				payload.userId(),
				variables);
	}
}
