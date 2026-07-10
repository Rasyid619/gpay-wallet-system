package com.gpay.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for event-id parsing from the Idempotency-Key header, covering
 * both producer key formats and the missing/malformed header branches.
 */
class NotificationEventIdsTest {

	private RecordHeaders headersWithKey(String idempotencyKey) {
		RecordHeaders headers = new RecordHeaders();
		headers.add("Idempotency-Key", idempotencyKey.getBytes(StandardCharsets.UTF_8));
		return headers;
	}

	@Test
	void parsesEventIdFromWalletOutboxKey() {
		UUID eventId = UUID.randomUUID();

		assertThat(NotificationEventIds.readEventId(headersWithKey("wallet-outbox-" + eventId)))
				.isEqualTo(eventId);
	}

	@Test
	void parsesEventIdFromPaymentOutboxKey() {
		UUID eventId = UUID.randomUUID();

		assertThat(NotificationEventIds.readEventId(headersWithKey("payment-outbox-" + eventId)))
				.isEqualTo(eventId);
	}

	@Test
	void rejectsMissingIdempotencyKeyHeader() {
		assertThatThrownBy(() -> NotificationEventIds.readEventId(new RecordHeaders()))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void rejectsHeaderWithNullValue() {
		RecordHeaders headers = new RecordHeaders();
		headers.add(new RecordHeader("Idempotency-Key", (byte[]) null));

		assertThatThrownBy(() -> NotificationEventIds.readEventId(headers))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void rejectsKeyWithoutOutboxMarker() {
		assertThatThrownBy(() -> NotificationEventIds.readEventId(headersWithKey("some-other-key")))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void rejectsKeyWithMalformedUuid() {
		assertThatThrownBy(() -> NotificationEventIds.readEventId(headersWithKey("wallet-outbox-not-a-uuid")))
				.isInstanceOf(NonRetryableNotificationException.class);
	}
}
