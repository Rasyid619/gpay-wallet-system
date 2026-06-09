package com.gpay.payment_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/* Verifies the payment service Spring context can start. */
@SpringBootTest
@TestPropertySource(properties = {
		"payment.gateway.top-up-url=http://localhost:8084/mock-gateway/top-up",
		"payment.gateway.timeout-ms=5000"
})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
