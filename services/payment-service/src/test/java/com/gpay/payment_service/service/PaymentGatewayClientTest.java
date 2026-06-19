package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.config.PaymentGatewayProperties;
import com.gpay.payment_service.constant.PaymentGatewayMode;
import com.gpay.payment_service.dto.TopUpRequest;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class PaymentGatewayClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sendsValidGatewayTopUpRequestWithTraceId() throws Exception {
		CapturedRequest capturedRequest = startServer(0);
		PaymentGatewayClient client = client(5000L);
		UUID paymentTransactionId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();

		client.requestTopUp(
				paymentTransactionId,
				new TopUpRequest(walletId, 75000L, PaymentGatewayMode.SUCCESS),
				"trace-payment");

		assertThat(capturedRequest.await()).isTrue();
		JsonNode body = objectMapper.readTree(capturedRequest.body());
		assertThat(body.get("payment_transaction_id").asText()).isEqualTo(paymentTransactionId.toString());
		assertThat(body.get("wallet_id").asText()).isEqualTo(walletId.toString());
		assertThat(body.get("amount").asLong()).isEqualTo(75000L);
		assertThat(body.get("mode").asText()).isEqualTo("SUCCESS");
		assertThat(capturedRequest.firstHeader("X-Trace-Id")).isEqualTo("trace-payment");
	}

	@Test
	void timeoutDoesNotCrashGatewayCall() throws Exception {
		startServer(200);
		PaymentGatewayClient client = client(50L);
		Instant startedAt = Instant.now();

		client.requestTopUp(
				UUID.randomUUID(),
				new TopUpRequest(UUID.randomUUID(), 75000L, PaymentGatewayMode.TIMEOUT),
				"trace-timeout");

		assertThat(Duration.between(startedAt, Instant.now()).toMillis()).isLessThan(1000);
	}

	@Test
	void sendsRequestWithoutTraceIdHeaderWhenTraceIdIsBlank() throws Exception {
		CapturedRequest capturedRequest = startServer(0);
		PaymentGatewayClient client = client(5000L);

		client.requestTopUp(
				UUID.randomUUID(),
				new TopUpRequest(UUID.randomUUID(), 75000L, PaymentGatewayMode.SUCCESS),
				"  ");

		assertThat(capturedRequest.await()).isTrue();
		assertThat(capturedRequest.traceId).isNull();
	}

	@Test
	void connectionFailureIsSwallowed() {
		PaymentGatewayProperties properties = new PaymentGatewayProperties(
				URI.create("http://localhost:1/mock-gateway/top-up"),
				5000L);
		PaymentGatewayClient client = new PaymentGatewayClient(properties, WebClient.builder());

		client.requestTopUp(
				UUID.randomUUID(),
				new TopUpRequest(UUID.randomUUID(), 75000L, PaymentGatewayMode.SUCCESS),
				"trace-conn-refused");
	}

	@Test
	void nonTimeoutNonRequestExceptionIsRethrown() {
		PaymentGatewayProperties properties = new PaymentGatewayProperties(
				URI.create("http://localhost:65535/mock-gateway/top-up"),
				5000L);
		WebClient.Builder builder = WebClient.builder()
				.filter((request, next) -> {
					throw WebClientResponseException.create(
							500, "Internal Server Error", null, null, null);
				});
		PaymentGatewayClient client = new PaymentGatewayClient(properties, builder);

		assertThatThrownBy(() -> client.requestTopUp(
				UUID.randomUUID(),
				new TopUpRequest(UUID.randomUUID(), 75000L, PaymentGatewayMode.SUCCESS),
				"trace-rethrow"))
				.isInstanceOf(WebClientResponseException.class);
	}

	private PaymentGatewayClient client(Long timeoutMs) {
		PaymentGatewayProperties properties = new PaymentGatewayProperties(
				URI.create("http://localhost:" + server.getAddress().getPort() + "/mock-gateway/top-up"),
				timeoutMs);
		return new PaymentGatewayClient(properties, WebClient.builder());
	}

	private CapturedRequest startServer(long responseDelayMs) throws IOException {
		CapturedRequest capturedRequest = new CapturedRequest();
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/mock-gateway/top-up", exchange -> {
			capturedRequest.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			capturedRequest.traceId = exchange.getRequestHeaders().get("X-Trace-Id");
			capturedRequest.latch.countDown();
			try {
				Thread.sleep(responseDelayMs);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		return capturedRequest;
	}

	private static class CapturedRequest {
		private final CountDownLatch latch = new CountDownLatch(1);
		private String body;
		private List<String> traceId;

		private boolean await() throws InterruptedException {
			return latch.await(1, TimeUnit.SECONDS);
		}

		private String body() {
			return body;
		}

		private String firstHeader(String headerName) {
			if ("X-Trace-Id".equals(headerName)) {
				return traceId.getFirst();
			}
			throw new IllegalArgumentException("Unexpected header: " + headerName);
		}
	}
}
