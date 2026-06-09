package com.gpay.mock_gateway_service.service;

import com.gpay.mock_gateway_service.constant.GatewayMode;
import com.gpay.mock_gateway_service.dto.MockGatewayTopUpRequest;
import com.gpay.mock_gateway_service.dto.MockGatewayTopUpResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MockGatewayTopUpService {

	@Value("${mock-gateway.timeout-delay-ms:6000}")
	private long timeoutDelayMs;

	public MockGatewayTopUpResponse topUp(MockGatewayTopUpRequest request) {
		if (request.mode() == GatewayMode.TIMEOUT) {
			simulateSlowResponse();
		}

		return new MockGatewayTopUpResponse(
				request.paymentTransactionId(),
				request.walletId(),
				request.amount(),
				request.mode().name(),
				request.mode().name(),
				"gw-" + UUID.randomUUID(),
				messageFor(request.mode()));
	}

	private void simulateSlowResponse() {
		try {
			Thread.sleep(timeoutDelayMs);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Mock gateway timeout simulation was interrupted", ex);
		}
	}

	private String messageFor(GatewayMode mode) {
		return switch (mode) {
			case SUCCESS -> "Gateway accepted successful top-up simulation";
			case FAILED -> "Gateway accepted failed top-up simulation";
			case TIMEOUT -> "Gateway timeout simulation completed";
		};
	}
}
