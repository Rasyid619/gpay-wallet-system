package com.gpay.mock_gateway_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.mock_gateway_service.config.MockGatewayProperties;
import com.gpay.mock_gateway_service.dto.GatewayWebhookPayload;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class PaymentWebhookClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sendsSignedWebhookPayloadWithTraceId() throws Exception {
		CapturedRequest capturedRequest = startServer();
		MockGatewayProperties properties = new MockGatewayProperties();
		properties.setWebhookSecret("webhook-secret");
		properties.setPaymentWebhookUrl(URI.create("http://localhost:" + server.getAddress().getPort() + "/payments/webhook/gateway"));
		GatewaySignatureService signatureService = new GatewaySignatureService(properties);
		PaymentWebhookClient client = new PaymentWebhookClient(
				WebClient.builder(),
				objectMapper,
				properties,
				signatureService);
		UUID paymentTransactionId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();

		client.send(new GatewayWebhookPayload(
				paymentTransactionId,
				walletId,
				75000L,
				"SUCCESS",
				"gw-reference"), "trace-callback");

		JsonNode body = objectMapper.readTree(capturedRequest.body());
		String timestamp = capturedRequest.firstHeader("X-Gateway-Timestamp");
		String signature = capturedRequest.firstHeader("X-Gateway-Signature");
		assertThat(body.get("payment_transaction_id").asText()).isEqualTo(paymentTransactionId.toString());
		assertThat(body.get("wallet_id").asText()).isEqualTo(walletId.toString());
		assertThat(body.get("amount").asLong()).isEqualTo(75000L);
		assertThat(body.get("status").asText()).isEqualTo("SUCCESS");
		assertThat(body.get("gateway_reference").asText()).isEqualTo("gw-reference");
		assertThat(capturedRequest.firstHeader("X-Trace-Id")).isEqualTo("trace-callback");
		assertThat(signature).isEqualTo(signatureService.sign(timestamp, capturedRequest.body()));
	}

	@Test
	void throwsWhenPaymentWebhookUrlIsNotConfigured() {
		MockGatewayProperties properties = new MockGatewayProperties();
		properties.setWebhookSecret("webhook-secret");
		PaymentWebhookClient client = new PaymentWebhookClient(
				WebClient.builder(),
				objectMapper,
				properties,
				new GatewaySignatureService(properties));

		assertThatThrownBy(() -> client.send(
						new GatewayWebhookPayload(UUID.randomUUID(), UUID.randomUUID(), 75000L, "SUCCESS", "gw-reference"),
						"trace-callback"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PAYMENT_WEBHOOK_URL");
	}

	@Test
	void omitsTraceIdHeaderWhenTraceIdIsBlank() throws Exception {
		CapturedRequest capturedRequest = startServer();
		MockGatewayProperties properties = new MockGatewayProperties();
		properties.setWebhookSecret("webhook-secret");
		properties.setPaymentWebhookUrl(URI.create("http://localhost:" + server.getAddress().getPort() + "/payments/webhook/gateway"));
		PaymentWebhookClient client = new PaymentWebhookClient(
				WebClient.builder(),
				objectMapper,
				properties,
				new GatewaySignatureService(properties));

		client.send(new GatewayWebhookPayload(
				UUID.randomUUID(),
				UUID.randomUUID(),
				75000L,
				"SUCCESS",
				"gw-reference"), "   ");

		assertThat(capturedRequest.traceIdHeaders()).isNull();
	}

	private CapturedRequest startServer() throws IOException {
		CapturedRequest capturedRequest = new CapturedRequest();
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/payments/webhook/gateway", exchange -> {
			capturedRequest.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			capturedRequest.signature = exchange.getRequestHeaders().get("X-Gateway-Signature");
			capturedRequest.timestamp = exchange.getRequestHeaders().get("X-Gateway-Timestamp");
			capturedRequest.traceId = exchange.getRequestHeaders().get("X-Trace-Id");
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		return capturedRequest;
	}

	private static class CapturedRequest {
		private String body;
		private List<String> signature;
		private List<String> timestamp;
		private List<String> traceId;

		private String body() {
			return body;
		}

		private List<String> traceIdHeaders() {
			return traceId;
		}

		private String firstHeader(String headerName) {
			return switch (headerName) {
				case "X-Gateway-Signature" -> signature.getFirst();
				case "X-Gateway-Timestamp" -> timestamp.getFirst();
				case "X-Trace-Id" -> traceId.getFirst();
				default -> throw new IllegalArgumentException("Unexpected header: " + headerName);
			};
		}
	}
}
