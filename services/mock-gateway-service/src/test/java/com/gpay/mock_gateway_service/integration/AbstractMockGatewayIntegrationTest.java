package com.gpay.mock_gateway_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared base for Mock Gateway Service integration tests that drive the real
 * controller -> service -> {@code PaymentWebhookClient} flow.
 *
 * <p>Mock Gateway has no database, so this base starts a single local
 * {@link HttpServer} that stands in for the Payment Service webhook endpoint.
 * The stub captures the outbound request method, headers, and raw body so tests
 * can verify webhook shape, HMAC signature, and trace-id propagation against the
 * real {@code PaymentWebhookClient} output. The server is started once in a
 * static block and reused across subclasses; captured state is reset before each
 * test.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractMockGatewayIntegrationTest {

	static final String WEBHOOK_PATH = "/payments/webhook/gateway";
	static final String TEST_WEBHOOK_SECRET = "integration-test-webhook-secret";

	private static final HttpServer WEBHOOK_SERVER;
	private static final CapturedWebhookRequest LAST_REQUEST = new CapturedWebhookRequest();

	static {
		WEBHOOK_SERVER = createWebhookServer();
		WEBHOOK_SERVER.start();
	}

	protected final MockMvc mockMvc;
	protected final ObjectMapper objectMapper;

	@Autowired
	AbstractMockGatewayIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
	}

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		registry.add(
				"mock-gateway.payment-webhook-url",
				() -> "http://localhost:" + WEBHOOK_SERVER.getAddress().getPort() + WEBHOOK_PATH);
		registry.add("mock-gateway.webhook-secret", () -> TEST_WEBHOOK_SECRET);
		registry.add("mock-gateway.timeout-delay-ms", () -> "10");
	}

	@BeforeEach
	void resetCapturedWebhook() {
		LAST_REQUEST.reset();
	}

	/**
	 * Returns the most recently captured webhook request, or {@code null} when no
	 * webhook reached the stub server since the last reset.
	 */
	protected CapturedWebhookRequest lastWebhookRequest() {
		return LAST_REQUEST.received ? LAST_REQUEST : null;
	}

	private static HttpServer createWebhookServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
			server.createContext(WEBHOOK_PATH, AbstractMockGatewayIntegrationTest::handle);
			return server;
		} catch (IOException ex) {
			throw new UncheckedIOException("Unable to start webhook stub server", ex);
		}
	}

	private static void handle(HttpExchange exchange) throws IOException {
		LAST_REQUEST.capture(exchange);
		exchange.sendResponseHeaders(200, -1);
		exchange.close();
	}

	/**
	 * Snapshot of the outbound webhook request observed by the stub server.
	 */
	protected static final class CapturedWebhookRequest {

		private volatile boolean received;
		private volatile String method;
		private volatile String body;
		private volatile List<String> signature;
		private volatile List<String> timestamp;
		private volatile List<String> traceId;

		private void capture(HttpExchange exchange) throws IOException {
			method = exchange.getRequestMethod();
			body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			signature = exchange.getRequestHeaders().get("X-Gateway-Signature");
			timestamp = exchange.getRequestHeaders().get("X-Gateway-Timestamp");
			traceId = exchange.getRequestHeaders().get("X-Trace-Id");
			received = true;
		}

		private void reset() {
			received = false;
			method = null;
			body = null;
			signature = null;
			timestamp = null;
			traceId = null;
		}

		String method() {
			return method;
		}

		String body() {
			return body;
		}

		String signatureHeader() {
			return firstOrNull(signature);
		}

		String timestampHeader() {
			return firstOrNull(timestamp);
		}

		String traceIdHeader() {
			return firstOrNull(traceId);
		}

		private static String firstOrNull(List<String> values) {
			return values == null || values.isEmpty() ? null : values.getFirst();
		}
	}
}
