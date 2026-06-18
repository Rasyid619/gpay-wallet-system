package com.gpay.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.auth_service.config.AuthWalletProperties;
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

/**
 * Unit tests for Auth Service wallet provisioning HTTP client behavior.
 */
class WalletProvisioningClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sendsInternalWalletProvisionRequestWithRequiredHeaders() throws Exception {
		CapturedRequest capturedRequest = startServer();
		WalletProvisioningClient client = client();
		UUID userId = UUID.randomUUID();

		client.provisionWallet(userId, "trace-wallet-provision");

		assertThat(capturedRequest.await()).isTrue();
		JsonNode body = objectMapper.readTree(capturedRequest.body());
		assertThat(body.get("user_id").asText()).isEqualTo(userId.toString());
		assertThat(capturedRequest.firstHeader("X-Internal-Token")).isEqualTo("test-internal-token");
		assertThat(capturedRequest.firstHeader("X-Trace-Id")).isEqualTo("trace-wallet-provision");
	}

	@Test
	void omitsTraceIdHeaderWhenTraceIdIsBlank() throws Exception {
		CapturedRequest capturedRequest = startServer();
		WalletProvisioningClient client = client();

		client.provisionWallet(UUID.randomUUID(), null);

		assertThat(capturedRequest.await()).isTrue();
		assertThat(capturedRequest.traceId).isNull();
	}

	private WalletProvisioningClient client() {
		AuthWalletProperties properties = new AuthWalletProperties(
				URI.create("http://localhost:" + server.getAddress().getPort() + "/internal/wallets/provision"),
				"test-internal-token",
				5000L);
		return new WalletProvisioningClient(properties, WebClient.builder());
	}

	private CapturedRequest startServer() throws IOException {
		CapturedRequest capturedRequest = new CapturedRequest();
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/internal/wallets/provision", exchange -> {
			capturedRequest.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			capturedRequest.internalToken = exchange.getRequestHeaders().get("X-Internal-Token");
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
				case "X-Trace-Id" -> traceId.getFirst();
				default -> throw new IllegalArgumentException("Unexpected header: " + headerName);
			};
		}
	}
}
