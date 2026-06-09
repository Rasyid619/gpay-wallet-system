package com.gpay.payment_service;

import com.gpay.payment_service.config.PaymentGatewayProperties;
import com.gpay.payment_service.config.PaymentOutboxProperties;
import com.gpay.payment_service.config.PaymentRateLimitProperties;
import com.gpay.payment_service.config.PaymentWebhookProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/* Bootstraps the payment service application. */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
		PaymentRateLimitProperties.class,
		PaymentGatewayProperties.class,
		PaymentOutboxProperties.class,
		PaymentWebhookProperties.class
})
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}
}
