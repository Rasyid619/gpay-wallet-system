package com.gpay.notification_service.service;

import com.gpay.notification_service.config.NotificationMailProperties;
import com.gpay.notification_service.exception.EmailDeliveryException;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends rendered HTML emails over SMTP, classifying failures so the Kafka
 * error handler retries transient faults and dead-letters permanent ones.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

	private final JavaMailSender javaMailSender;
	private final NotificationMailProperties properties;

	/**
	 * Sends one HTML email from the configured sender address.
	 *
	 * @param recipientEmail recipient address
	 * @param subject        email subject line
	 * @param htmlBody       rendered HTML body
	 * @throws EmailDeliveryException            on transient SMTP failures
	 * @throws NonRetryableNotificationException on malformed message or address,
	 *                                           or SMTP authentication misconfiguration
	 */
	public void send(String recipientEmail, String subject, String htmlBody) {
		MimeMessage message = javaMailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(properties.fromAddress());
			helper.setTo(recipientEmail);
			helper.setSubject(subject);
			helper.setText(htmlBody, true);
		} catch (MessagingException | MailParseException ex) {
			throw new NonRetryableNotificationException("Email message could not be composed", ex);
		}

		try {
			javaMailSender.send(message);
		} catch (MailAuthenticationException ex) {
			throw new NonRetryableNotificationException("SMTP authentication is misconfigured", ex);
		} catch (MailSendException ex) {
			throw new EmailDeliveryException("Email could not be delivered to the SMTP server", ex);
		}
	}
}
