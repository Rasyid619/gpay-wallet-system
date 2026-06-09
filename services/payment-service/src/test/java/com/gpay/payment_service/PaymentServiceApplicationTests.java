package com.gpay.payment_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/* Verifies the payment service Spring context can start. */
@SpringBootTest
@TestPropertySource(properties = {
		"payment.gateway.top-up-url=http://localhost:8084/mock-gateway/top-up",
		"payment.gateway.timeout-ms=5000",
		"payment.webhook.gateway-secret=test-gateway-webhook-secret",
		"payment.outbox.wallet-credit-url=http://localhost:8082/internal/wallets/credit",
		"payment.outbox.wallet-internal-token=test-internal-token",
		"payment.outbox.request-timeout-ms=5000",
		"payment.outbox.retry-delay-ms=60000",
		"payment.outbox.batch-size=10",
		"payment.outbox.worker-fixed-delay-ms=3600000",
		"payment.outbox.worker-initial-delay-ms=3600000"
})
class PaymentServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
