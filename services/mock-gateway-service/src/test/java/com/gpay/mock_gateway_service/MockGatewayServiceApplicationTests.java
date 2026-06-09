package com.gpay.mock_gateway_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"mock-gateway.timeout-delay-ms=10",
		"mock-gateway.payment-webhook-url=http://localhost:8083/payments/webhook/gateway",
		"mock-gateway.webhook-secret=test-webhook-secret"
})
class MockGatewayServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
