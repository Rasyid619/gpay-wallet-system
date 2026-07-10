package com.gpay.notification_service.service;

import com.gpay.common.tracing.TraceIdContext;
import com.gpay.notification_service.constant.NotificationType;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/* Renders transactional email HTML from Thymeleaf templates. */
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

	private final SpringTemplateEngine emailTemplateEngine;

	/**
	 * Renders the HTML body for one notification.
	 *
	 * <p>The {@code amount} variable is a {@code Long} in whole IDR and is
	 * formatted as {@code Rp10,000}; the current trace id is exposed to the
	 * template footer as {@code trace_id}.
	 *
	 * @param type           kind of transactional email
	 * @param recipientEmail recipient shown in the greeting
	 * @param variables      template values including the {@code amount}
	 * @return rendered HTML body
	 * @throws NonRetryableNotificationException when rendering fails
	 */
	public String render(NotificationType type, String recipientEmail, Map<String, Object> variables) {
		Context context = new Context();
		context.setVariables(variables);
		context.setVariable("recipient_email", recipientEmail);
		context.setVariable("formatted_amount", formatAmount((Long) variables.get("amount")));
		context.setVariable("trace_id", TraceIdContext.getTraceId());

		try {
			return emailTemplateEngine.process(type.getTemplateName(), context);
		} catch (RuntimeException ex) {
			throw new NonRetryableNotificationException("Email template could not be rendered", ex);
		}
	}

	private String formatAmount(Long amount) {
		if (amount == null) {
			return null;
		}
		return String.format(Locale.US, "Rp%,d", amount);
	}
}
