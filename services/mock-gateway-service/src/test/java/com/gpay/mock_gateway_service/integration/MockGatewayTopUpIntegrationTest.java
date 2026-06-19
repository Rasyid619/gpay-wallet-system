package com.gpay.mock_gateway_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration coverage for {@code POST /mock-gateway/top-up}, driving the real
 * controller -> service -> {@code PaymentWebhookClient} flow for every gateway
 * mode and asserting webhook shape, HMAC signature, and trace-id propagation
 * against the request captured by the stub webhook server.
 */
class MockGatewayTopUpIntegrationTest extends AbstractMockGatewayIntegrationTest {

	private static final String HMAC_SHA256 = "HmacSHA256";

	@Autowired
	MockGatewayTopUpIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
		super(mockMvc, objectMapper);
	}

	@Nested
	class SuccessMode {

		@Test
		void returnsSuccessResponseAndSendsWebhook() throws Exception {
			UUID paymentTransactionId = UUID.randomUUID();
			UUID walletId = UUID.randomUUID();

			mockMvc.perform(post("/mock-gateway/top-up")
							.contentType(MediaType.APPLICATION_JSON)
							.content(topUpRequest(paymentTransactionId, walletId, 75000L, "SUCCESS")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.payment_transaction_id").value(paymentTransactionId.toString()))
					.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
					.andExpect(jsonPath("$.amount").value(75000))
					.andExpect(jsonPath("$.mode").value("SUCCESS"))
					.andExpect(jsonPath("$.status").value("SUCCESS"))
					.andExpect(jsonPath("$.gateway_reference", startsWith("gw-")));

			CapturedWebhookRequest webhook = lastWebhookRequest();
			assertThat(webhook).isNotNull();

			JsonNode body = objectMapper.readTree(webhook.body());
			assertThat(body.get("payment_transaction_id").asText()).isEqualTo(paymentTransactionId.toString());
			assertThat(body.get("wallet_id").asText()).isEqualTo(walletId.toString());
			assertThat(body.get("amount").asLong()).isEqualTo(75000L);
			assertThat(body.get("status").asText()).isEqualTo("SUCCESS");
			assertThat(body.get("gateway_reference").asText()).startsWith("gw-");
		}

		@Test
		void signsWebhookWithGatewaySecret() throws Exception {
			mockMvc.perform(post("/mock-gateway/top-up")
							.contentType(MediaType.APPLICATION_JSON)
							.content(topUpRequest(UUID.randomUUID(), UUID.randomUUID(), 50000L, "SUCCESS")))
					.andExpect(status().isOk());

			CapturedWebhookRequest webhook = lastWebhookRequest();
			assertThat(webhook).isNotNull();

			String expectedSignature = hmacSha256(
					TEST_WEBHOOK_SECRET,
					webhook.timestampHeader() + "." + webhook.body());
			assertThat(webhook.signatureHeader()).isEqualTo(expectedSignature);
		}

		@Test
		void propagatesProvidedTraceIdToWebhookAndResponse() throws Exception {
			mockMvc.perform(post("/mock-gateway/top-up")
							.header("X-Trace-Id", "trace-from-caller")
							.contentType(MediaType.APPLICATION_JSON)
							.content(topUpRequest(UUID.randomUUID(), UUID.randomUUID(), 50000L, "SUCCESS")))
					.andExpect(status().isOk())
					.andExpect(header().string("X-Trace-Id", "trace-from-caller"));

			CapturedWebhookRequest webhook = lastWebhookRequest();
			assertThat(webhook).isNotNull();
			assertThat(webhook.traceIdHeader()).isEqualTo("trace-from-caller");
		}

		@Test
		void generatesTraceIdForWebhookWhenHeaderAbsent() throws Exception {
			String responseTraceId = mockMvc.perform(post("/mock-gateway/top-up")
							.contentType(MediaType.APPLICATION_JSON)
							.content(topUpRequest(UUID.randomUUID(), UUID.randomUUID(), 50000L, "SUCCESS")))
					.andExpect(status().isOk())
					.andExpect(header().exists("X-Trace-Id"))
					.andReturn()
					.getResponse()
					.getHeader("X-Trace-Id");

			CapturedWebhookRequest webhook = lastWebhookRequest();
			assertThat(webhook).isNotNull();
			assertThat(responseTraceId).isNotBlank();
			assertThat(webhook.traceIdHeader()).isEqualTo(responseTraceId);
		}
	}

	@Nested
	class FailedMode {

		@Test
		void returnsFailedResponseAndSendsFailedWebhook() throws Exception {
			mockMvc.perform(post("/mock-gateway/top-up")
							.contentType(MediaType.APPLICATION_JSON)
							.content(topUpRequest(UUID.randomUUID(), UUID.randomUUID(), 75000L, "FAILED")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.mode").value("FAILED"))
					.andExpect(jsonPath("$.status").value("FAILED"));

			CapturedWebhookRequest webhook = lastWebhookRequest();
			assertThat(webhook).isNotNull();

			JsonNode body = objectMapper.readTree(webhook.body());
			assertThat(body.get("status").asText()).isEqualTo("FAILED");
		}
	}

	@Nested
	class TimeoutMode {

		@Test
		void returnsTimeoutResponseWithoutSendingWebhook() throws Exception {
			mockMvc.perform(post("/mock-gateway/top-up")
							.contentType(MediaType.APPLICATION_JSON)
							.content(topUpRequest(UUID.randomUUID(), UUID.randomUUID(), 75000L, "TIMEOUT")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.mode").value("TIMEOUT"))
					.andExpect(jsonPath("$.status").value("TIMEOUT"));

			assertThat(lastWebhookRequest()).isNull();
		}
	}

	@Nested
	class Validation {

		@Test
		void rejectsRequestWithMissingMode() throws Exception {
			mockMvc.perform(post("/mock-gateway/top-up")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
									  "payment_transaction_id": "%s",
									  "wallet_id": "%s",
									  "amount": 75000
									}
									""".formatted(UUID.randomUUID(), UUID.randomUUID())))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

			assertThat(lastWebhookRequest()).isNull();
		}

		@Test
		void rejectsRequestWithNonPositiveAmount() throws Exception {
			mockMvc.perform(post("/mock-gateway/top-up")
							.contentType(MediaType.APPLICATION_JSON)
							.content(topUpRequest(UUID.randomUUID(), UUID.randomUUID(), 0L, "SUCCESS")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

			assertThat(lastWebhookRequest()).isNull();
		}
	}

	private static String topUpRequest(UUID paymentTransactionId, UUID walletId, Long amount, String mode) {
		return """
				{
				  "payment_transaction_id": "%s",
				  "wallet_id": "%s",
				  "amount": %d,
				  "mode": "%s"
				}
				""".formatted(paymentTransactionId, walletId, amount, mode);
	}

	private static String hmacSha256(String secret, String data) throws Exception {
		Mac mac = Mac.getInstance(HMAC_SHA256);
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
		byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(signature);
	}
}
