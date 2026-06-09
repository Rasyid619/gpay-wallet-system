package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.exception.InvalidWebhookSignatureException;
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
		"payment.webhook.gateway-secret=test-gateway-webhook-secret"
})
class PaymentWebhookServiceTest {

	@Autowired
	private GatewayWebhookSignatureService signatureService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PaymentWebhookService paymentWebhookService;

	@Autowired
	private TopupTransactionRepository topupTransactionRepository;

	@Test
	void validSuccessWebhookUpdatesTransaction() {
		TopupTransaction transaction = pendingTransaction();
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
}
