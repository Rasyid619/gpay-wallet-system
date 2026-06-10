package com.gpay.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.constant.PaymentStatus;
import com.gpay.payment_service.dto.GatewayWebhookRequest;
import com.gpay.payment_service.dto.GatewayWebhookResponse;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.exception.BadRequestException;
import com.gpay.payment_service.exception.NotFoundException;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

	private final GatewayWebhookSignatureService signatureService;
	private final ObjectMapper objectMapper;
	private final PaymentActivityLogService paymentActivityLogService;
	private final PaymentOutboxService paymentOutboxService;
	private final TopupTransactionRepository topupTransactionRepository;

	@Transactional
	public GatewayWebhookResponse processGatewayWebhook(
			String signature,
			String timestamp,
			String rawRequestBody) {
		long startedAtNanos = System.nanoTime();
		signatureService.validate(timestamp, rawRequestBody, signature);
		GatewayWebhookRequest request = readRequest(rawRequestBody);

		TopupTransaction transaction = topupTransactionRepository.findLockedById(request.paymentTransactionId())
				.orElseThrow(() -> new NotFoundException("Payment transaction was not found"));
		if (!transaction.getWalletId().equals(request.walletId()) || !transaction.getAmount().equals(request.amount())) {
			throw new BadRequestException("Gateway webhook does not match payment transaction");
		}

		Instant now = Instant.now();
		boolean shouldLogStatusTransition = transaction.getStatus() != request.status();
		if (request.status() == PaymentStatus.SUCCESS) {
			transaction.markSuccess(request.gatewayReference(), now);
			paymentOutboxService.enqueueWalletCredit(transaction, now);
		} else if (request.status() == PaymentStatus.FAILED) {
			transaction.markFailed(request.gatewayReference(), "Gateway reported payment failure", now);
		} else {
			throw new BadRequestException("Gateway webhook status must be SUCCESS or FAILED");
		}

		GatewayWebhookResponse response = new GatewayWebhookResponse(transaction.getId(), transaction.getStatus().name());
		if (shouldLogStatusTransition) {
			paymentActivityLogService.logGatewayWebhookProcessed(
					transaction,
					request,
					response,
					durationMs(startedAtNanos),
					now);
		}
		return response;
	}

	private Long durationMs(long startedAtNanos) {
		return (System.nanoTime() - startedAtNanos) / 1_000_000L;
	}

	private GatewayWebhookRequest readRequest(String rawRequestBody) {
		try {
			GatewayWebhookRequest request = objectMapper.readValue(rawRequestBody, GatewayWebhookRequest.class);
			validateRequest(request);
			return request;
		} catch (JsonProcessingException ex) {
			throw new BadRequestException("Gateway webhook request body is invalid");
		}
	}

	private void validateRequest(GatewayWebhookRequest request) {
		if (request.paymentTransactionId() == null
				|| request.walletId() == null
				|| request.amount() == null
				|| request.amount() <= 0
				|| request.status() == null
				|| request.gatewayReference() == null
				|| request.gatewayReference().isBlank()) {
			throw new BadRequestException("Gateway webhook request body is invalid");
		}
	}
}
