package com.gpay.mock_gateway_service.service;

import com.gpay.mock_gateway_service.config.MockGatewayProperties;
import com.gpay.mock_gateway_service.constant.GatewayMode;
import com.gpay.mock_gateway_service.dto.GatewayWebhookPayload;
import com.gpay.mock_gateway_service.dto.MockGatewayTopUpRequest;
import com.gpay.mock_gateway_service.dto.MockGatewayTopUpResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MockGatewayTopUpService {

	private final MockGatewayProperties properties;
	private final PaymentWebhookClient paymentWebhookClient;

	public MockGatewayTopUpResponse topUp(MockGatewayTopUpRequest request, String traceId) {
		if (request.mode() == GatewayMode.TIMEOUT) {
			simulateSlowResponse();
		}

		String gatewayReference = "gw-" + UUID.randomUUID();
		if (request.mode() == GatewayMode.SUCCESS || request.mode() == GatewayMode.FAILED) {
			paymentWebhookClient.send(new GatewayWebhookPayload(
					request.paymentTransactionId(),
					request.walletId(),
					request.amount(),
					request.mode().name(),
					gatewayReference), traceId);
		}

		return new MockGatewayTopUpResponse(
				request.paymentTransactionId(),
				request.walletId(),
				request.amount(),
				request.mode().name(),
				request.mode().name(),
				gatewayReference,
				messageFor(request.mode()));
	}

	private void simulateSlowResponse() {
		try {
			Thread.sleep(properties.getTimeoutDelayMs());
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
