package com.gpay.mock_gateway_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gpay.mock_gateway_service.config.MockGatewayProperties;
import org.junit.jupiter.api.Test;

class GatewaySignatureServiceTest {

	@Test
	void signsTimestampAndExactRawBodyWithHmacSha256() {
		MockGatewayProperties properties = new MockGatewayProperties();
		properties.setWebhookSecret("secret");
		GatewaySignatureService service = new GatewaySignatureService(properties);

		String signature = service.sign("1700000000", "{\"status\":\"SUCCESS\"}");

		assertThat(signature).isEqualTo("559f9b1e000d60a15b9fbe80a3e78d2e7271981ef5ce07c9a81f2905ef3f209d");
	}

	@Test
	void rejectsBlankWebhookSecret() {
		GatewaySignatureService service = new GatewaySignatureService(new MockGatewayProperties());

		assertThatThrownBy(() -> service.sign("1700000000", "{}"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("GATEWAY_WEBHOOK_SECRET must be configured");
	}
}
