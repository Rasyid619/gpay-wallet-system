package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.config.PaymentOutboxProperties;
import com.gpay.payment_service.dto.WalletCreditOutboxPayload;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class WalletCreditClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sendsInternalWalletCreditRequestWithRequiredHeaders() throws Exception {
		CapturedRequest capturedRequest = startServer();
		WalletCreditClient client = client();
		UUID walletId = UUID.randomUUID();
		UUID paymentTransactionId = UUID.randomUUID();

		client.creditWallet(
				new WalletCreditOutboxPayload(walletId, paymentTransactionId, 75000L),
				"payment-outbox-event-id",
				"trace-wallet-credit");

		assertThat(capturedRequest.await()).isTrue();
		JsonNode body = objectMapper.readTree(capturedRequest.body());
		assertThat(body.get("wallet_id").asText()).isEqualTo(walletId.toString());
		assertThat(body.get("payment_transaction_id").asText()).isEqualTo(paymentTransactionId.toString());
		assertThat(body.get("amount").asLong()).isEqualTo(75000L);
		assertThat(capturedRequest.firstHeader("X-Internal-Token")).isEqualTo("test-internal-token");
		assertThat(capturedRequest.firstHeader("Idempotency-Key")).isEqualTo("payment-outbox-event-id");
		assertThat(capturedRequest.firstHeader("X-Trace-Id")).isEqualTo("trace-wallet-credit");
	}

	private WalletCreditClient client() {
		PaymentOutboxProperties properties = new PaymentOutboxProperties(
				URI.create("http://localhost:" + server.getAddress().getPort() + "/internal/wallets/credit"),
				"test-internal-token",
				5000L,
				60000L,
				10,
				86400000L,
				300000L,
				10,
				3600000L,
				3600000L);
		return new WalletCreditClient(properties, WebClient.builder());
	}

	private CapturedRequest startServer() throws IOException {
		CapturedRequest capturedRequest = new CapturedRequest();
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/internal/wallets/credit", exchange -> {
			capturedRequest.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			capturedRequest.internalToken = exchange.getRequestHeaders().get("X-Internal-Token");
			capturedRequest.idempotencyKey = exchange.getRequestHeaders().get("Idempotency-Key");
			capturedRequest.traceId = exchange.getRequestHeaders().get("X-Trace-Id");
			capturedRequest.latch.countDown();
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		return capturedRequest;
	}

	private static class CapturedRequest {
		private final CountDownLatch latch = new CountDownLatch(1);
		private String body;
		private List<String> idempotencyKey;
		private List<String> internalToken;
		private List<String> traceId;

		private boolean await() throws InterruptedException {
			return latch.await(1, TimeUnit.SECONDS);
		}

		private String body() {
			return body;
		}

		private String firstHeader(String headerName) {
			return switch (headerName) {
				case "X-Internal-Token" -> internalToken.getFirst();
				case "Idempotency-Key" -> idempotencyKey.getFirst();
				case "X-Trace-Id" -> traceId.getFirst();
				default -> throw new IllegalArgumentException("Unexpected header: " + headerName);
			};
		}
	}
}
