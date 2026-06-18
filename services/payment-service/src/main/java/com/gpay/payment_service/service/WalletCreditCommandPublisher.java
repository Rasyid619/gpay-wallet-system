package com.gpay.payment_service.service;

import com.gpay.common.tracing.KafkaTraceIdPropagation;
import com.gpay.common.tracing.TraceIdContext;
import com.gpay.payment_service.config.PaymentKafkaProperties;
import com.gpay.payment_service.dto.WalletCreditOutboxPayload;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Publishes wallet credit commands to Kafka and blocks until the broker
 * acknowledges, so the outbox row is only marked processed after a durable ack.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletCreditCommandPublisher {

	private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	private final KafkaTemplate<String, WalletCreditOutboxPayload> kafkaTemplate;
	private final PaymentKafkaProperties properties;

	/**
	 * Sends a wallet credit command and waits for the broker acknowledgement.
	 *
	 * @param payload        wallet credit command payload
	 * @param idempotencyKey durable idempotency key carried as a record header
	 * @param traceId        trace id to propagate to the consumer
	 * @throws InterruptedException if the publishing thread is interrupted
	 * @throws ExecutionException   if the broker rejects the publish
	 */
	public void publish(WalletCreditOutboxPayload payload, String idempotencyKey, String traceId)
			throws InterruptedException, ExecutionException {
		String key = payload.walletId().toString();
		ProducerRecord<String, WalletCreditOutboxPayload> record = new ProducerRecord<>(
				properties.walletCreditCommandsTopic(),
				key,
				payload);
		record.headers().add(IDEMPOTENCY_KEY_HEADER, idempotencyKey.getBytes(StandardCharsets.UTF_8));

		String previousTraceId = MDC.get(TraceIdContext.TRACE_ID_KEY);
		applyTraceId(traceId);
		KafkaTraceIdPropagation.injectTraceId(record.headers());

		try {
			SendResult<String, WalletCreditOutboxPayload> result = kafkaTemplate.send(record).get();
			log.info(
					"Published wallet credit command idempotencyKey={} partition={} offset={}",
					idempotencyKey,
					result.getRecordMetadata().partition(),
					result.getRecordMetadata().offset());
		} finally {
			restoreTraceId(previousTraceId);
		}
	}

	private void applyTraceId(String traceId) {
		if (traceId == null || traceId.isBlank()) {
			return;
		}
		MDC.put(TraceIdContext.TRACE_ID_KEY, traceId);
	}

	private void restoreTraceId(String previousTraceId) {
		if (previousTraceId == null) {
			MDC.remove(TraceIdContext.TRACE_ID_KEY);
			return;
		}
		MDC.put(TraceIdContext.TRACE_ID_KEY, previousTraceId);
	}
}
