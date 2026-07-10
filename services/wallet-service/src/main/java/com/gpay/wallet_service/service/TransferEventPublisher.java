package com.gpay.wallet_service.service;

import com.gpay.common.tracing.KafkaTraceIdPropagation;
import com.gpay.common.tracing.TraceIdContext;
import com.gpay.wallet_service.config.WalletKafkaProperties;
import com.gpay.wallet_service.constant.OutboxEventType;
import com.gpay.wallet_service.dto.TransferEventOutboxPayload;
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
 * Publishes transfer-result events to Kafka and blocks until the broker
 * acknowledges, so the outbox row is only marked processed after a durable ack.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferEventPublisher {

	private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	private final KafkaTemplate<String, TransferEventOutboxPayload> transferEventKafkaTemplate;
	private final WalletKafkaProperties properties;

	/**
	 * Sends a transfer-result event and waits for the broker acknowledgement.
	 *
	 * @param eventType      completed or failed transfer event kind
	 * @param payload        transfer event payload
	 * @param idempotencyKey durable idempotency key carried as a record header
	 * @param traceId        trace id to propagate to the consumer
	 * @throws InterruptedException if the publishing thread is interrupted
	 * @throws ExecutionException   if the broker rejects the publish
	 */
	public void publish(
			OutboxEventType eventType,
			TransferEventOutboxPayload payload,
			String idempotencyKey,
			String traceId) throws InterruptedException, ExecutionException {
		String key = payload.senderWalletId().toString();
		ProducerRecord<String, TransferEventOutboxPayload> record = new ProducerRecord<>(
				topicFor(eventType),
				key,
				payload);
		record.headers().add(IDEMPOTENCY_KEY_HEADER, idempotencyKey.getBytes(StandardCharsets.UTF_8));

		String previousTraceId = MDC.get(TraceIdContext.TRACE_ID_KEY);
		applyTraceId(traceId);
		KafkaTraceIdPropagation.injectTraceId(record.headers());

		try {
			SendResult<String, TransferEventOutboxPayload> result = transferEventKafkaTemplate.send(record).get();
			log.info(
					"Published transfer event type={} idempotencyKey={} partition={} offset={}",
					eventType,
					idempotencyKey,
					result.getRecordMetadata().partition(),
					result.getRecordMetadata().offset());
		} finally {
			restoreTraceId(previousTraceId);
		}
	}

	private String topicFor(OutboxEventType eventType) {
		return switch (eventType) {
			case TRANSFER_COMPLETED -> properties.transferCompletedTopic();
			case TRANSFER_RECEIVED -> properties.transferReceivedTopic();
			case TRANSFER_FAILED -> properties.transferFailedTopic();
		};
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
