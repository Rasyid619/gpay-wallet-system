package com.gpay.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.notification_service.config.NotificationMailProperties;
import com.gpay.notification_service.exception.EmailDeliveryException;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Unit tests for SMTP sending, covering from-address composition and the
 * transient-vs-permanent failure classification.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

	@Mock
	private JavaMailSender javaMailSender;

	private EmailService emailService;

	@BeforeEach
	void setUp() {
		when(javaMailSender.createMimeMessage())
				.thenReturn(new JavaMailSenderImpl().createMimeMessage());
		emailService = new EmailService(
				javaMailSender,
				new NotificationMailProperties("localhost", 1025, "no-reply@gpay.local"));
	}

	@Test
	void sendsHtmlEmailFromConfiguredAddress() throws Exception {
		emailService.send("recipient@example.com", "Subject", "<html>body</html>");

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(javaMailSender).send(captor.capture());
		MimeMessage sent = captor.getValue();
		assertThat(sent.getFrom()[0].toString()).isEqualTo("no-reply@gpay.local");
		assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("recipient@example.com");
		assertThat(sent.getSubject()).isEqualTo("Subject");
	}

	@Test
	void classifiesSmtpSendFailureAsRetryable() {
		doThrow(new MailSendException("connection refused"))
				.when(javaMailSender).send(any(MimeMessage.class));

		assertThatThrownBy(() -> emailService.send("recipient@example.com", "Subject", "<html>body</html>"))
				.isInstanceOf(EmailDeliveryException.class);
	}

	@Test
	void classifiesSmtpAuthenticationFailureAsNonRetryable() {
		doThrow(new MailAuthenticationException("bad credentials"))
				.when(javaMailSender).send(any(MimeMessage.class));

		assertThatThrownBy(() -> emailService.send("recipient@example.com", "Subject", "<html>body</html>"))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void classifiesMalformedRecipientAddressAsNonRetryable() {
		assertThatThrownBy(() -> emailService.send("not an address", "Subject", "<html>body</html>"))
				.isInstanceOf(NonRetryableNotificationException.class);
	}
}
