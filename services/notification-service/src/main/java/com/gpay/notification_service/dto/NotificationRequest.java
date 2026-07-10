package com.gpay.notification_service.dto;

import com.gpay.notification_service.constant.NotificationType;
import java.util.Map;
import java.util.UUID;

/**
 * One notification to deliver, distilled from a consumed event.
 *
 * @param eventId           unique event identifier used for idempotent dedup
 * @param type              kind of transactional email to send
 * @param recipientUserId   auth-service user id whose email receives the message
 * @param templateVariables values rendered into the email template
 */
public record NotificationRequest(
		UUID eventId,
		NotificationType type,
		UUID recipientUserId,
		Map<String, Object> templateVariables) {
}
