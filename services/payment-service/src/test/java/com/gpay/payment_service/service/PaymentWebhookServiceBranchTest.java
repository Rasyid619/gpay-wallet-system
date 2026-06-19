package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.config.PaymentWebhookProperties;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.exception.BadRequestException;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Branch-focused unit tests for {@link PaymentWebhookService} body validation, the
 * wallet/amount match guard, and the unsupported-status path, using a real signature
 * service so the helper produces matching HMAC values for arbitrary raw bodies.
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceBranchTest {

	private static final String SECRET = "branch-test-gateway-webhook-secret";
	private static final String TIMESTAMP = "2026-06-09T10:00:00Z";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GatewayWebhookSignatureService signatureService =
			new GatewayWebhookSignatureService(new PaymentWebhookProperties(SECRET));

	@Mock
	private PaymentActivityLogService paymentActivityLogService;

	@Mock
	private PaymentOutboxService paymentOutboxService;

	@Mock
	private TopupTransactionRepository topupTransactionRepository;

	private PaymentWebhookService service() {
		return new PaymentWebhookService(
				signatureService,
				objectMapper,
				paymentActivityLogService,
				paymentOutboxService,
				topupTransactionRepository);
	}

	private TopupTransaction pendingTransaction(UUID id, UUID walletId, long amount) {
		return TopupTransaction.createPending(
				id, UUID.randomUUID(), walletId, amount, "key", "trace", Instant.now());
	}

	private void invokeWebhook(String rawBody) {
		String signature = signatureService.sign(TIMESTAMP, rawBody);
		service().processGatewayWebhook(signature, TIMESTAMP, rawBody);
	}

	@Test
	void rejectsBodyWithBlankGatewayReference() {
		UUID id = UUID.randomUUID();
		String body = """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":75000,"status":"SUCCESS","gateway_reference":" "}"""
				.formatted(id, UUID.randomUUID());

		assertThatThrownBy(() -> invokeWebhook(body))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("request body is invalid");
	}

	@Test
	void rejectsBodyMissingPaymentTransactionId() {
		String body = """
				{"wallet_id":"%s","amount":75000,"status":"SUCCESS","gateway_reference":"gw"}"""
				.formatted(UUID.randomUUID());

		assertThatThrownBy(() -> invokeWebhook(body))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("request body is invalid");
	}

	@Test
	void rejectsBodyMissingWalletId() {
		String body = """
				{"payment_transaction_id":"%s","amount":75000,"status":"SUCCESS","gateway_reference":"gw"}"""
				.formatted(UUID.randomUUID());

		assertThatThrownBy(() -> invokeWebhook(body))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("request body is invalid");
	}

	@Test
	void rejectsBodyMissingAmount() {
		String body = """
				{"payment_transaction_id":"%s","wallet_id":"%s","status":"SUCCESS","gateway_reference":"gw"}"""
				.formatted(UUID.randomUUID(), UUID.randomUUID());

		assertThatThrownBy(() -> invokeWebhook(body))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("request body is invalid");
	}

	@Test
	void rejectsBodyWithNonPositiveAmount() {
		String body = """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":0,"status":"SUCCESS","gateway_reference":"gw"}"""
				.formatted(UUID.randomUUID(), UUID.randomUUID());

		assertThatThrownBy(() -> invokeWebhook(body))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("request body is invalid");
	}

	@Test
	void rejectsBodyMissingStatus() {
		String body = """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":75000,"gateway_reference":"gw"}"""
				.formatted(UUID.randomUUID(), UUID.randomUUID());

		assertThatThrownBy(() -> invokeWebhook(body))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("request body is invalid");
	}

	@Test
	void rejectsBodyMissingGatewayReference() {
		String body = """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":75000,"status":"SUCCESS"}"""
				.formatted(UUID.randomUUID(), UUID.randomUUID());

		assertThatThrownBy(() -> invokeWebhook(body))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("request body is invalid");
	}

	@Test
	void rejectsBodyWithMalformedJson() {
		assertThatThrownBy(() -> invokeWebhook("not-json"))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("request body is invalid");
	}

	@Test
	void rejectsWebhookWhenWalletDoesNotMatchTransaction() {
		UUID id = UUID.randomUUID();
		UUID transactionWallet = UUID.randomUUID();
		UUID webhookWallet = UUID.randomUUID();
		when(topupTransactionRepository.findLockedById(id))
				.thenReturn(Optional.of(pendingTransaction(id, transactionWallet, 75_000L)));
		String body = """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":75000,"status":"SUCCESS","gateway_reference":"gw"}"""
				.formatted(id, webhookWallet);

		assertThatThrownBy(() -> invokeWebhook(body))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("does not match payment transaction");
	}

	@Test
	void rejectsWebhookWithPendingStatus() {
		UUID id = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		lenient().when(topupTransactionRepository.findLockedById(id))
				.thenReturn(Optional.of(pendingTransaction(id, walletId, 75_000L)));
		String body = """
				{"payment_transaction_id":"%s","wallet_id":"%s","amount":75000,"status":"PENDING","gateway_reference":"gw"}"""
				.formatted(id, walletId);

		assertThatThrownBy(() -> invokeWebhook(body))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("status must be SUCCESS or FAILED");
	}
}
