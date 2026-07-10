package com.gpay.notification_service.service;

import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/**
 * Derives the unique event id from the {@code Idempotency-Key} record header.
 *
 * <p>Producers set the header to {@code <service>-outbox-<outboxEventId>}, so the
 * UUID after the {@code outbox-} marker identifies one outbox event across
 * redeliveries and is used for idempotent notification dedup.
 */
public final class NotificationEventIds {

	private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
	private static final String OUTBOX_MARKER = "outbox-";

	private NotificationEventIds() {
	}

	/**
	 * Reads and parses the event id from the idempotency key header.
	 *
	 * @param headers inbound Kafka record headers
	 * @return unique event id carried by the idempotency key
	 * @throws NonRetryableNotificationException when the header is missing or malformed
	 */
	public static UUID readEventId(Headers headers) {
		Header header = headers.lastHeader(IDEMPOTENCY_KEY_HEADER);
		if (header == null || header.value() == null) {
			throw new NonRetryableNotificationException("Idempotency-Key header is required");
		}

		String idempotencyKey = new String(header.value(), StandardCharsets.UTF_8);
		int markerIndex = idempotencyKey.lastIndexOf(OUTBOX_MARKER);
		if (markerIndex < 0) {
			throw new NonRetryableNotificationException("Idempotency-Key header is malformed");
		}

		try {
			return UUID.fromString(idempotencyKey.substring(markerIndex + OUTBOX_MARKER.length()));
		} catch (IllegalArgumentException ex) {
			throw new NonRetryableNotificationException("Idempotency-Key header is malformed", ex);
		}
	}
}
