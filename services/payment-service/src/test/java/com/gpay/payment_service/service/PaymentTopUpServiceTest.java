package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.constant.PaymentGatewayMode;
import com.gpay.payment_service.dto.IdempotentResponse;
import com.gpay.payment_service.dto.TopUpRequest;
import com.gpay.payment_service.dto.TopUpResponse;
import com.gpay.payment_service.entity.ActivityLog;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.exception.BadRequestException;
import com.gpay.payment_service.exception.IdempotencyConflictException;
import com.gpay.payment_service.repository.ActivityLogRepository;
import com.gpay.payment_service.repository.IdempotencyKeyRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for payment top-up creation and durable idempotency.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"payment.gateway.top-up-url=http://localhost:8084/mock-gateway/top-up",
		"payment.gateway.timeout-ms=5000",
		"payment.webhook.gateway-secret=test-gateway-webhook-secret",
		"payment.outbox.wallet-credit-url=http://localhost:8082/internal/wallets/credit",
		"payment.outbox.wallet-internal-token=test-internal-token",
		"payment.outbox.request-timeout-ms=5000",
		"payment.outbox.retry-delay-ms=60000",
		"payment.outbox.max-attempts=10",
		"payment.outbox.processing-timeout-ms=300000",
		"payment.outbox.batch-size=10",
		"payment.outbox.worker-fixed-delay-ms=3600000",
		"payment.outbox.worker-initial-delay-ms=3600000"
})
class PaymentTopUpServiceTest {

	@Autowired
	private ActivityLogRepository activityLogRepository;

	@Autowired
	private IdempotencyKeyRepository idempotencyKeyRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PaymentTopUpService paymentTopUpService;

	@MockitoBean
	private PaymentRateLimiter paymentRateLimiter;

	@MockitoBean
	private PaymentGatewayClient paymentGatewayClient;

	@Autowired
	private TopupTransactionRepository topupTransactionRepository;

	@Test
	void createsPendingTopUpTransactionAndStoresIdempotencyResponse() {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		String idempotencyKey = "topup-create-" + UUID.randomUUID();

		TopUpRequest request = new TopUpRequest(walletId, 75000L, PaymentGatewayMode.SUCCESS);
		IdempotentResponse result = paymentTopUpService.topUp(
				userId,
				idempotencyKey,
				request,
				"trace-topup");

		assertThat(result.status()).isEqualTo(201);
		assertThat(result.body()).isInstanceOf(TopUpResponse.class);
		TopUpResponse body = (TopUpResponse) result.body();
		assertThat(body.walletId()).isEqualTo(walletId);
		assertThat(body.amount()).isEqualTo(75000L);
		assertThat(body.status()).isEqualTo("PENDING");

		TopupTransaction transaction = topupTransactionRepository.findById(body.paymentTransactionId()).orElseThrow();
		assertThat(transaction.getUserId()).isEqualTo(userId);
		assertThat(transaction.getWalletId()).isEqualTo(walletId);
		assertThat(transaction.getAmount()).isEqualTo(75000L);
		assertThat(transaction.getStatus().name()).isEqualTo("PENDING");
		assertThat(transaction.getIdempotencyKey()).isEqualTo(idempotencyKey);
		assertThat(transaction.getTraceId()).isEqualTo("trace-topup");
		ActivityLog activityLog = activityLogRepository
				.findByTransactionIdOrderByCreatedAtAsc(body.paymentTransactionId())
				.getFirst();
		assertThat(activityLog.getUserId()).isEqualTo(userId);
		assertThat(activityLog.getTransactionId()).isEqualTo(body.paymentTransactionId());
		assertThat(activityLog.getServiceName()).isEqualTo("payment-service");
		assertThat(activityLog.getAction()).isEqualTo("PAYMENT_TOP_UP");
		assertThat(activityLog.getStatus()).isEqualTo("PENDING");
		assertThat(activityLog.getTraceId()).isEqualTo("trace-topup");
		assertThat(activityLog.getDurationMs()).isNotNull();
		assertThat(readPayload(activityLog.getRequestPayload()).get("amount").asLong()).isEqualTo(75000L);
		assertThat(readPayload(activityLog.getResponsePayload()).get("status").asText()).isEqualTo("PENDING");
		assertThat(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)).isPresent();
		verify(paymentGatewayClient).requestTopUp(eq(body.paymentTransactionId()), eq(request), eq("trace-topup"));
	}

	@Test
	void createsPendingTopUpTransactionForTimeoutGatewayMode() {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		String idempotencyKey = "topup-timeout-" + UUID.randomUUID();
		TopUpRequest request = new TopUpRequest(walletId, 75000L, PaymentGatewayMode.TIMEOUT);

		IdempotentResponse result = paymentTopUpService.topUp(
				userId,
				idempotencyKey,
				request,
				"trace-timeout");

		TopUpResponse body = (TopUpResponse) result.body();
		TopupTransaction transaction = topupTransactionRepository.findById(body.paymentTransactionId()).orElseThrow();
		assertThat(result.status()).isEqualTo(201);
		assertThat(body.status()).isEqualTo("PENDING");
		assertThat(transaction.getStatus().name()).isEqualTo("PENDING");
		verify(paymentGatewayClient).requestTopUp(eq(body.paymentTransactionId()), eq(request), eq("trace-timeout"));
	}

	@Test
	void replaysSameIdempotencyKeyAndPayloadWithoutCreatingAnotherTransaction() {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		String idempotencyKey = "topup-replay-" + UUID.randomUUID();
		TopUpRequest request = new TopUpRequest(walletId, 75000L, PaymentGatewayMode.SUCCESS);
		long countBefore = topupTransactionRepository.count();

		IdempotentResponse first = paymentTopUpService.topUp(userId, idempotencyKey, request, "trace-first");
		IdempotentResponse second = paymentTopUpService.topUp(userId, idempotencyKey, request, "trace-second");

		TopUpResponse firstBody = (TopUpResponse) first.body();
		assertThat(second.status()).isEqualTo(201);
		assertThat(second.body()).isInstanceOf(JsonNode.class);
		JsonNode secondBody = (JsonNode) second.body();
		assertThat(secondBody.get("payment_transaction_id").asText()).isEqualTo(firstBody.paymentTransactionId().toString());
		assertThat(secondBody.get("wallet_id").asText()).isEqualTo(walletId.toString());
		assertThat(secondBody.get("amount").asLong()).isEqualTo(75000L);
		assertThat(topupTransactionRepository.count()).isEqualTo(countBefore + 1);
	}

	@Test
	void allowsSameIdempotencyKeyForDifferentUsers() {
		String idempotencyKey = "topup-shared-key-" + UUID.randomUUID();

		IdempotentResponse first = paymentTopUpService.topUp(
				UUID.randomUUID(),
				idempotencyKey,
				new TopUpRequest(UUID.randomUUID(), 75000L, PaymentGatewayMode.SUCCESS),
				"trace-first");
		IdempotentResponse second = paymentTopUpService.topUp(
				UUID.randomUUID(),
				idempotencyKey,
				new TopUpRequest(UUID.randomUUID(), 75000L, PaymentGatewayMode.SUCCESS),
				"trace-second");

		assertThat(first.status()).isEqualTo(201);
		assertThat(second.status()).isEqualTo(201);
	}

	@Test
	void rejectsSameIdempotencyKeyWithDifferentPayloadForSameUser() {
		UUID userId = UUID.randomUUID();
		String idempotencyKey = "topup-conflict-" + UUID.randomUUID();

		paymentTopUpService.topUp(
				userId,
				idempotencyKey,
				new TopUpRequest(UUID.randomUUID(), 75000L, PaymentGatewayMode.SUCCESS),
				"trace-conflict");

		assertThatThrownBy(() -> paymentTopUpService.topUp(
				userId,
				idempotencyKey,
				new TopUpRequest(UUID.randomUUID(), 75000L, PaymentGatewayMode.FAILED),
				"trace-conflict"))
				.isInstanceOf(IdempotencyConflictException.class);
	}

	@Test
	void rejectsBlankIdempotencyKey() {
		assertThatThrownBy(() -> paymentTopUpService.topUp(
				UUID.randomUUID(),
				" ",
				new TopUpRequest(UUID.randomUUID(), 75000L, PaymentGatewayMode.SUCCESS),
				"trace-blank"))
				.isInstanceOf(BadRequestException.class);
	}

	private JsonNode readPayload(String payload) {
		try {
			return objectMapper.readTree(payload);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Activity payload should be valid JSON", ex);
		}
	}
}
