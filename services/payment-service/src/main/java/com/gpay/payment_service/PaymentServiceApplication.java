package com.gpay.payment_service;

import com.gpay.payment_service.config.PaymentRateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/* Bootstraps the payment service application. */
@SpringBootApplication
@EnableConfigurationProperties(PaymentRateLimitProperties.class)
public class PaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}
}
