package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.entity.ActivityLog;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.exception.InvalidWebhookSignatureException;
import com.gpay.payment_service.repository.ActivityLogRepository;
import com.gpay.payment_service.repository.OutboxEventRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"payment.gateway.top-up-url=http://localhost:8084/mock-gateway/top-up",
		"payment.gateway.timeout-ms=5000",
		"payment.webhook.gateway-secret=test-gateway-webhook-secret",
		"payment.outbox.wallet-credit-url=http://localhost:8082/internal/wallets/credit",
		"payment.outbox.wallet-internal-token=test-internal-token",
		"payment.outbox.request-timeout-ms=5000",
		"payment.outbox.retry-delay-ms=60000",
		"payment.outbox.processing-timeout-ms=300000",
		"payment.outbox.batch-size=10",
		"payment.outbox.worker-fixed-delay-ms=3600000",
		"payment.outbox.worker-initial-delay-ms=3600000"
})
class PaymentWebhookServiceTest {

	@Autowired
	private ActivityLogRepository activityLogRepository;

	@Autowired
	private GatewayWebhookSignatureService signatureService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PaymentWebhookService paymentWebhookService;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TopupTransactionRepository topupTransactionRepository;

	@Test
	void validSuccessWebhookUpdatesTransactionAndCreatesOutboxEvent() {
		TopupTransaction transaction = pendingTransaction();
		long outboxCountBefore = countOutboxEvents();
		String rawBody = webhookBody(transaction, "SUCCESS", "gw-success");
		String timestamp = "2026-06-09T10:00:00Z";
		String signature = signatureService.sign(timestamp, rawBody);

		var response = paymentWebhookService.processGatewayWebhook(signature, timestamp, rawBody);

		TopupTransaction updated = topupTransactionRepository.findById(transaction.getId()).orElseThrow();
		assertThat(response.paymentTransactionId()).isEqualTo(transaction.getId());
		assertThat(response.status()).isEqualTo("SUCCESS");
		assertThat(updated.getStatus().name()).isEqualTo("SUCCESS");
		assertThat(updated.getGatewayReference()).isEqualTo("gw-success");
		assertThat(updated.getFailureReason()).isNull();
		assertThat(countOutboxEvents()).isEqualTo(outboxCountBefore + 1);
		var outboxEvent = outboxEventRepository.findAll()
				.stream()
				.filter(event -> event.getAggregateId().equals(transaction.getId()))
				.findFirst()
				.orElseThrow();
		assertThat(outboxEvent.getEventType().name()).isEqualTo("CREDIT_WALLET_REQUESTED");
		assertThat(outboxEvent.getStatus().name()).isEqualTo("PENDING");
		assertThat(outboxEvent.getRetryCount()).isZero();
		assertThat(outboxEvent.getNextRetryAt()).isNotNull();
		ActivityLog activityLog = activityLogRepository
				.findByTransactionIdOrderByCreatedAtAsc(transaction.getId())
				.getFirst();
		assertThat(activityLog.getUserId()).isEqualTo(transaction.getUserId());
		assertThat(activityLog.getTransactionId()).isEqualTo(transaction.getId());
		assertThat(activityLog.getServiceName()).isEqualTo("payment-service");
		assertThat(activityLog.getAction()).isEqualTo("PAYMENT_GATEWAY_WEBHOOK");
		assertThat(activityLog.getStatus()).isEqualTo("SUCCESS");
		assertThat(activityLog.getTraceId()).isEqualTo("trace-webhook");
		assertThat(activityLog.getDurationMs()).isNotNull();
		assertThat(readPayload(activityLog.getRequestPayload()).get("status").asText()).isEqualTo("SUCCESS");
		assertThat(readPayload(activityLog.getResponsePayload()).get("status").asText()).isEqualTo("SUCCESS");
		var payload = readPayload(outboxEvent.getPayload());
		assertThat(payload.get("wallet_id").asText()).isEqualTo(transaction.getWalletId().toString());
		assertThat(payload.get("payment_transaction_id").asText()).isEqualTo(transaction.getId().toString());
		assertThat(payload.get("amount").asLong()).isEqualTo(75000L);
	}

	@Test
	void duplicateSuccessWebhookDoesNotCreateDuplicateOutboxEvent() {
		TopupTransaction transaction = pendingTransaction();
		long outboxCountBefore = countOutboxEvents();
		String rawBody = webhookBody(transaction, "SUCCESS", "gw-duplicate");
		String timestamp = "2026-06-09T10:00:00Z";
		String signature = signatureService.sign(timestamp, rawBody);

		paymentWebhookService.processGatewayWebhook(signature, timestamp, rawBody);
		paymentWebhookService.processGatewayWebhook(signature, timestamp, rawBody);

		assertThat(countOutboxEvents()).isEqualTo(outboxCountBefore + 1);
		assertThat(activityLogRepository.findByTransactionIdOrderByCreatedAtAsc(transaction.getId())).hasSize(1);
	}

	@Test
	void validFailedWebhookUpdatesTransactionWithoutOutboxEvent() {
		TopupTransaction transaction = pendingTransaction();
		long outboxCountBefore = countOutboxEvents();
		String rawBody = webhookBody(transaction, "FAILED", "gw-failed");
		String timestamp = "2026-06-09T10:00:00Z";
		String signature = signatureService.sign(timestamp, rawBody);

		var response = paymentWebhookService.processGatewayWebhook(signature, timestamp, rawBody);

		TopupTransaction updated = topupTransactionRepository.findById(transaction.getId()).orElseThrow();
		assertThat(response.status()).isEqualTo("FAILED");
		assertThat(updated.getStatus().name()).isEqualTo("FAILED");
		assertThat(updated.getGatewayReference()).isEqualTo("gw-failed");
		assertThat(updated.getFailureReason()).isEqualTo("Gateway reported payment failure");
		assertThat(countOutboxEvents()).isEqualTo(outboxCountBefore);
		ActivityLog activityLog = activityLogRepository
				.findByTransactionIdOrderByCreatedAtAsc(transaction.getId())
				.getFirst();
		assertThat(activityLog.getAction()).isEqualTo("PAYMENT_GATEWAY_WEBHOOK");
		assertThat(activityLog.getStatus()).isEqualTo("FAILED");
		assertThat(activityLog.getTraceId()).isEqualTo("trace-webhook");
		assertThat(readPayload(activityLog.getResponsePayload()).get("status").asText()).isEqualTo("FAILED");
	}

	@Test
	void invalidSignatureIsRejectedBeforeTransactionMutation() {
		TopupTransaction transaction = pendingTransaction();
		long outboxCountBefore = countOutboxEvents();
		String rawBody = webhookBody(transaction, "SUCCESS", "gw-invalid");

		assertThatThrownBy(() -> paymentWebhookService.processGatewayWebhook(
				"bad-signature",
				"2026-06-09T10:00:00Z",
				rawBody))
				.isInstanceOf(InvalidWebhookSignatureException.class);

		TopupTransaction unchanged = topupTransactionRepository.findById(transaction.getId()).orElseThrow();
		assertThat(unchanged.getStatus().name()).isEqualTo("PENDING");
		assertThat(unchanged.getGatewayReference()).isNull();
		assertThat(countOutboxEvents()).isEqualTo(outboxCountBefore);
		assertThat(activityLogRepository.findByTransactionIdOrderByCreatedAtAsc(transaction.getId())).isEmpty();
	}

	private TopupTransaction pendingTransaction() {
		Instant now = Instant.parse("2026-06-09T10:00:00Z");
		return topupTransactionRepository.save(TopupTransaction.createPending(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				75000L,
				"webhook-key-" + UUID.randomUUID(),
				"trace-webhook",
				now));
	}

	private String webhookBody(TopupTransaction transaction, String status, String gatewayReference) {
		return """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":75000,"status":"%s","gateway_reference":"%s"}"""
				.formatted(transaction.getId(), transaction.getWalletId(), status, gatewayReference);
	}

	private long countOutboxEvents() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Long.class);
	}

	private com.fasterxml.jackson.databind.JsonNode readPayload(String payload) {
		try {
			return objectMapper.readTree(payload);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Outbox payload should be valid JSON", ex);
		}
	}
}
