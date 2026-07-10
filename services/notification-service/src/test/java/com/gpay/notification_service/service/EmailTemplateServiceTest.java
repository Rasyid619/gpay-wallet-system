package com.gpay.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gpay.common.tracing.TraceIdContext;
import com.gpay.notification_service.config.NotificationMailConfig;
import com.gpay.notification_service.config.NotificationMailProperties;
import com.gpay.notification_service.constant.NotificationType;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Unit tests for Thymeleaf email rendering against the real templates,
 * covering whole-IDR amount formatting, failure reasons, the trace footer,
 * and the missing-template failure branch.
 */
class EmailTemplateServiceTest {

	private final EmailTemplateService emailTemplateService = new EmailTemplateService(
			new NotificationMailConfig(new NotificationMailProperties("localhost", 1025, "no-reply@gpay.local"))
					.emailTemplateEngine());

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	private Map<String, Object> variables(Long amount, String failureReason) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("transaction_id", UUID.fromString("11111111-2222-3333-4444-555555555555"));
		variables.put("sender_wallet_id", UUID.fromString("99999999-8888-7777-6666-555555555555"));
		variables.put("amount", amount);
		variables.put("failure_reason", failureReason);
		return variables;
	}

	@Test
	void rendersTransferCompletedWithFormattedWholeIdrAmount() {
		String html = emailTemplateService.render(
				NotificationType.TRANSFER_COMPLETED,
				"user@example.com",
				variables(10_000L, null));

		assertThat(html).contains("Rp10,000");
		assertThat(html).contains("user@example.com");
		assertThat(html).contains("11111111-2222-3333-4444-555555555555");
	}

	@Test
	void rendersTransferReceivedWithSenderWalletForTheReceiver() {
		String html = emailTemplateService.render(
				NotificationType.TRANSFER_RECEIVED,
				"receiver@example.com",
				variables(10_000L, null));

		assertThat(html).contains("You received");
		assertThat(html).contains("Rp10,000");
		assertThat(html).contains("receiver@example.com");
		assertThat(html).contains("99999999-8888-7777-6666-555555555555");
	}

	@Test
	void rendersTransferFailedWithFailureReason() {
		String html = emailTemplateService.render(
				NotificationType.TRANSFER_FAILED,
				"user@example.com",
				variables(75_000L, "INSUFFICIENT_BALANCE"));

		assertThat(html).contains("Rp75,000");
		assertThat(html).contains("INSUFFICIENT_BALANCE");
	}

	@Test
	void rendersTopupSucceededWithZeroAmountBoundary() {
		String html = emailTemplateService.render(
				NotificationType.TOPUP_SUCCEEDED,
				"user@example.com",
				variables(0L, null));

		assertThat(html).contains("Rp0");
	}

	@Test
	void rendersTopupFailedWithTraceIdFooter() {
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-render");

		String html = emailTemplateService.render(
				NotificationType.TOPUP_FAILED,
				"user@example.com",
				variables(50_000L, "Gateway reported payment failure"));

		assertThat(html).contains("trace-render");
		assertThat(html).contains("Gateway reported payment failure");
	}

	@Test
	void omitsFailureReasonParagraphWhenReasonIsNull() {
		String html = emailTemplateService.render(
				NotificationType.TRANSFER_FAILED,
				"user@example.com",
				variables(75_000L, null));

		assertThat(html).doesNotContain("Reason:");
	}

	@Test
	void rendersWithoutFormattedAmountWhenAmountIsAbsent() {
		String html = emailTemplateService.render(
				NotificationType.TRANSFER_COMPLETED,
				"user@example.com",
				variables(null, null));

		assertThat(html).doesNotContain("Rp");
	}

	@Test
	void throwsNonRetryableWhenTemplateCannotBeRendered() {
		ClassLoaderTemplateResolver missingResolver = new ClassLoaderTemplateResolver();
		missingResolver.setPrefix("missing/");
		missingResolver.setSuffix(".html");
		SpringTemplateEngine brokenEngine = new SpringTemplateEngine();
		brokenEngine.setTemplateResolver(missingResolver);
		EmailTemplateService brokenService = new EmailTemplateService(brokenEngine);

		assertThatThrownBy(() -> brokenService.render(
				NotificationType.TRANSFER_COMPLETED,
				"user@example.com",
				variables(1L, null)))
				.isInstanceOf(NonRetryableNotificationException.class);
	}
}
