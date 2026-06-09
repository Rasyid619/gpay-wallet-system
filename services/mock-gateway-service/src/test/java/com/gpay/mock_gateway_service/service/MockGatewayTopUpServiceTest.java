package com.gpay.mock_gateway_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gpay.mock_gateway_service.config.MockGatewayProperties;
import com.gpay.mock_gateway_service.constant.GatewayMode;
import com.gpay.mock_gateway_service.dto.GatewayWebhookPayload;
import com.gpay.mock_gateway_service.dto.MockGatewayTopUpRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MockGatewayTopUpServiceTest {

	private final MockGatewayProperties properties = new MockGatewayProperties();
	private final PaymentWebhookClient paymentWebhookClient = mock(PaymentWebhookClient.class);
	private final MockGatewayTopUpService service = new MockGatewayTopUpService(properties, paymentWebhookClient);

	MockGatewayTopUpServiceTest() {
		properties.setTimeoutDelayMs(10L);
	}

	@Test
	void sendsSuccessWebhookCallback() {
		UUID paymentTransactionId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();

		var response = service.topUp(new MockGatewayTopUpRequest(
				paymentTransactionId,
				walletId,
				75000L,
				GatewayMode.SUCCESS), "trace-success");

		ArgumentCaptor<GatewayWebhookPayload> payloadCaptor = ArgumentCaptor.forClass(GatewayWebhookPayload.class);
		verify(paymentWebhookClient).send(payloadCaptor.capture(), eq("trace-success"));
		GatewayWebhookPayload payload = payloadCaptor.getValue();
		assertThat(payload.paymentTransactionId()).isEqualTo(paymentTransactionId);
		assertThat(payload.walletId()).isEqualTo(walletId);
		assertThat(payload.amount()).isEqualTo(75000L);
		assertThat(payload.status()).isEqualTo("SUCCESS");
		assertThat(payload.gatewayReference()).isEqualTo(response.gatewayReference());
		assertThat(response.status()).isEqualTo("SUCCESS");
	}

	@Test
	void sendsFailedWebhookCallback() {
		service.topUp(new MockGatewayTopUpRequest(
				UUID.randomUUID(),
				UUID.randomUUID(),
				75000L,
				GatewayMode.FAILED), "trace-failed");

		ArgumentCaptor<GatewayWebhookPayload> payloadCaptor = ArgumentCaptor.forClass(GatewayWebhookPayload.class);
		verify(paymentWebhookClient).send(payloadCaptor.capture(), eq("trace-failed"));
		assertThat(payloadCaptor.getValue().status()).isEqualTo("FAILED");
	}

	@Test
	void timeoutModeDoesNotSendWebhookCallback() {
		properties.setTimeoutDelayMs(1L);

		var response = service.topUp(new MockGatewayTopUpRequest(
				UUID.randomUUID(),
				UUID.randomUUID(),
				75000L,
				GatewayMode.TIMEOUT), "trace-timeout");

		assertThat(response.status()).isEqualTo("TIMEOUT");
		verifyNoInteractions(paymentWebhookClient);
	}
}
