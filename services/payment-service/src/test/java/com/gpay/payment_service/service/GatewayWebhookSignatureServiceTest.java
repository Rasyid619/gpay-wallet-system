package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gpay.payment_service.config.PaymentWebhookProperties;
import com.gpay.payment_service.exception.InvalidWebhookSignatureException;
import org.junit.jupiter.api.Test;

class GatewayWebhookSignatureServiceTest {

	@Test
	void signsTimestampAndExactRawBodyWithHmacSha256() {
		GatewayWebhookSignatureService service = new GatewayWebhookSignatureService(
				new PaymentWebhookProperties("secret"));

		String signature = service.sign("1700000000", "{\"status\":\"SUCCESS\"}");

		assertThat(signature).isEqualTo("559f9b1e000d60a15b9fbe80a3e78d2e7271981ef5ce07c9a81f2905ef3f209d");
	}

	@Test
	void rejectsInvalidSignature() {
		GatewayWebhookSignatureService service = new GatewayWebhookSignatureService(
				new PaymentWebhookProperties("secret"));

		assertThatThrownBy(() -> service.validate("1700000000", "{\"status\":\"SUCCESS\"}", "bad-signature"))
				.isInstanceOf(InvalidWebhookSignatureException.class);
	}
}
