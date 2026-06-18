package com.gpay.wallet_service.service;

import com.gpay.common.tracing.KafkaTraceIdPropagation;
import com.gpay.common.tracing.TraceIdContext;
import com.gpay.wallet_service.dto.InternalWalletCreditRequest;
import com.gpay.wallet_service.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes wallet credit commands from Kafka and applies them idempotently
 * through the shared internal credit workflow.
 *
 * <p>The trace id is restored from the record header into MDC before handling and
 * cleared afterwards. Duplicate deliveries are safely ignored by the underlying
 * idempotency-key and payment-transaction dedup. Non-retryable failures bubble up
 * so the configured error handler can route them to the dead-letter topic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletCreditCommandConsumer {

	private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	private final InternalWalletCreditService internalWalletCreditService;

	@Value("${wallet.internal-token}")
	private String internalToken;

	@KafkaListener(
			topics = "${wallet.kafka.wallet-credit-commands-topic}",
			containerFactory = "walletCreditListenerContainerFactory")
	public void onWalletCreditCommand(ConsumerRecord<String, InternalWalletCreditRequest> record) {
		KafkaTraceIdPropagation.restoreTraceId(record.headers());
		try {
			handle(record);
		} finally {
			KafkaTraceIdPropagation.clearTraceId();
		}
	}

	private void handle(ConsumerRecord<String, InternalWalletCreditRequest> record) {
		String idempotencyKey = readIdempotencyKey(record);
		InternalWalletCreditRequest request = record.value();
		if (request == null) {
			throw new BadRequestException("Wallet credit command payload is missing");
		}

		log.info(
				"Consuming wallet credit command idempotencyKey={} walletId={} paymentTransactionId={}",
				idempotencyKey,
				request.walletId(),
				request.paymentTransactionId());
		internalWalletCreditService.credit(
				internalToken,
				idempotencyKey,
				request,
				TraceIdContext.getTraceId());
	}

	private String readIdempotencyKey(ConsumerRecord<String, InternalWalletCreditRequest> record) {
		Header header = record.headers().lastHeader(IDEMPOTENCY_KEY_HEADER);
		if (header == null || header.value() == null) {
			throw new BadRequestException("Idempotency-Key header is required");
		}
		return new String(header.value(), StandardCharsets.UTF_8);
	}
}
