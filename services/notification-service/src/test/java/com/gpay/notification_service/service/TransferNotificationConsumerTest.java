package com.gpay.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.gpay.notification_service.constant.NotificationType;
import com.gpay.notification_service.dto.NotificationRequest;
import com.gpay.notification_service.dto.WalletTransferEventPayload;
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
 * Unit tests for the transfer event consumer, covering delegation with the
 * parsed event id per topic kind and the malformed-record guards.
 */
@ExtendWith(MockitoExtension.class)
class TransferNotificationConsumerTest {

	@Mock
	private NotificationService notificationService;

	private ConsumerRecord<String, WalletTransferEventPayload> record(
			WalletTransferEventPayload payload,
			String idempotencyKey) {
		ConsumerRecord<String, WalletTransferEventPayload> record = new ConsumerRecord<>(
				"wallet.transfer.completed", 0, 0L, "key", payload);
		if (idempotencyKey != null) {
			record.headers().add("Idempotency-Key", idempotencyKey.getBytes(StandardCharsets.UTF_8));
		}
		return record;
	}

	private WalletTransferEventPayload payload(UUID userId) {
		return new WalletTransferEventPayload(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				userId,
				40_000L,
				null);
	}

	@Test
	void delegatesCompletedEventWithParsedEventId() {
		TransferNotificationConsumer consumer = new TransferNotificationConsumer(notificationService);
		UUID eventId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		consumer.onTransferCompleted(record(payload(userId), "wallet-outbox-" + eventId));

		ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
		verify(notificationService).deliver(captor.capture());
		NotificationRequest request = captor.getValue();
		assertThat(request.eventId()).isEqualTo(eventId);
		assertThat(request.type()).isEqualTo(NotificationType.TRANSFER_COMPLETED);
		assertThat(request.recipientUserId()).isEqualTo(userId);
		assertThat(request.templateVariables()).containsEntry("amount", 40_000L);
	}

	@Test
	void delegatesReceivedEventWithTransferReceivedTypeAndSenderWallet() {
		TransferNotificationConsumer consumer = new TransferNotificationConsumer(notificationService);
		WalletTransferEventPayload payload = payload(UUID.randomUUID());

		consumer.onTransferReceived(record(payload, "wallet-outbox-" + UUID.randomUUID()));

		ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
		verify(notificationService).deliver(captor.capture());
		assertThat(captor.getValue().type()).isEqualTo(NotificationType.TRANSFER_RECEIVED);
		assertThat(captor.getValue().templateVariables())
				.containsEntry("sender_wallet_id", payload.senderWalletId());
	}

	@Test
	void delegatesFailedEventWithTransferFailedType() {
		TransferNotificationConsumer consumer = new TransferNotificationConsumer(notificationService);

		consumer.onTransferFailed(record(payload(UUID.randomUUID()), "wallet-outbox-" + UUID.randomUUID()));

		ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
		verify(notificationService).deliver(captor.capture());
		assertThat(captor.getValue().type()).isEqualTo(NotificationType.TRANSFER_FAILED);
	}

	@Test
	void rejectsRecordWithMissingPayload() {
		TransferNotificationConsumer consumer = new TransferNotificationConsumer(notificationService);

		assertThatThrownBy(() -> consumer.onTransferCompleted(record(null, "wallet-outbox-" + UUID.randomUUID())))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void rejectsRecordWithMissingUserId() {
		TransferNotificationConsumer consumer = new TransferNotificationConsumer(notificationService);
		WalletTransferEventPayload payload = new WalletTransferEventPayload(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, 40_000L, null);

		assertThatThrownBy(() -> consumer.onTransferCompleted(record(payload, "wallet-outbox-" + UUID.randomUUID())))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void rejectsRecordWithMissingAmount() {
		TransferNotificationConsumer consumer = new TransferNotificationConsumer(notificationService);
		WalletTransferEventPayload payload = new WalletTransferEventPayload(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);

		assertThatThrownBy(() -> consumer.onTransferCompleted(record(payload, "wallet-outbox-" + UUID.randomUUID())))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void rejectsRecordWithMissingIdempotencyKeyHeader() {
		TransferNotificationConsumer consumer = new TransferNotificationConsumer(notificationService);

		assertThatThrownBy(() -> consumer.onTransferCompleted(record(payload(UUID.randomUUID()), null)))
				.isInstanceOf(NonRetryableNotificationException.class);
	}
}
