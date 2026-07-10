package com.gpay.notification_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Configures the SMTP sender from environment-backed properties and the
 * Thymeleaf engine that renders HTML email templates from the classpath.
 */
@Configuration
@RequiredArgsConstructor
public class NotificationMailConfig {

	private final NotificationMailProperties properties;

	@Bean
	public JavaMailSender javaMailSender() {
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost(properties.host());
		mailSender.setPort(properties.port());
		return mailSender;
	}

	@Bean
	public SpringTemplateEngine emailTemplateEngine() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCacheable(true);

		SpringTemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.setTemplateResolver(resolver);
		return templateEngine;
	}
}
