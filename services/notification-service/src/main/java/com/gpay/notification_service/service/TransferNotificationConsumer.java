package com.gpay.notification_service.service;

import com.gpay.common.tracing.KafkaTraceIdPropagation;
import com.gpay.notification_service.constant.NotificationType;
import com.gpay.notification_service.dto.NotificationRequest;
import com.gpay.notification_service.dto.WalletTransferEventPayload;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes wallet transfer result events and delivers the matching emails.
 *
 * <p>The trace id is restored from the record header into MDC before handling
 * and cleared afterwards. Duplicate deliveries are ignored by the event-id
 * dedup in {@link NotificationService}. Non-retryable failures bubble up so
 * the configured error handler routes them to the dead-letter topic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferNotificationConsumer {

	private final NotificationService notificationService;

	@KafkaListener(
			topics = "${notification.kafka.transfer-completed-topic}",
			containerFactory = "transferEventListenerContainerFactory")
	public void onTransferCompleted(ConsumerRecord<String, WalletTransferEventPayload> record) {
		consume(record, NotificationType.TRANSFER_COMPLETED);
	}

	@KafkaListener(
			topics = "${notification.kafka.transfer-received-topic}",
			containerFactory = "transferEventListenerContainerFactory")
	public void onTransferReceived(ConsumerRecord<String, WalletTransferEventPayload> record) {
		consume(record, NotificationType.TRANSFER_RECEIVED);
	}

	@KafkaListener(
			topics = "${notification.kafka.transfer-failed-topic}",
			containerFactory = "transferEventListenerContainerFactory")
	public void onTransferFailed(ConsumerRecord<String, WalletTransferEventPayload> record) {
		consume(record, NotificationType.TRANSFER_FAILED);
	}

	private void consume(ConsumerRecord<String, WalletTransferEventPayload> record, NotificationType type) {
		KafkaTraceIdPropagation.restoreTraceId(record.headers());
		try {
			notificationService.deliver(toRequest(record, type));
		} finally {
			KafkaTraceIdPropagation.clearTraceId();
		}
	}

	private NotificationRequest toRequest(
			ConsumerRecord<String, WalletTransferEventPayload> record,
			NotificationType type) {
		WalletTransferEventPayload payload = record.value();
		if (payload == null || payload.userId() == null || payload.amount() == null) {
			throw new NonRetryableNotificationException("Transfer event payload is missing or incomplete");
		}

		Map<String, Object> variables = new HashMap<>();
		variables.put("transaction_id", payload.transferId());
		variables.put("sender_wallet_id", payload.senderWalletId());
		variables.put("amount", payload.amount());
		variables.put("failure_reason", payload.failureReason());
		return new NotificationRequest(
				NotificationEventIds.readEventId(record.headers()),
				type,
				payload.userId(),
				variables);
	}
}
