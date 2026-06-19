package com.gpay.payment_service;

import com.gpay.payment_service.support.PaymentTestContainers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/* Verifies the payment service Spring context can start against the shared test containers. */
@SpringBootTest
class PaymentServiceApplicationTests {

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		PaymentTestContainers.registerProperties(registry);
	}

	@Test
	void contextLoads() {
	}
}
