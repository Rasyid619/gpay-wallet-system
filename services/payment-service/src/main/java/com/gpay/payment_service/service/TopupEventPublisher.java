package com.gpay.payment_service.service;

import com.gpay.common.tracing.KafkaTraceIdPropagation;
import com.gpay.common.tracing.TraceIdContext;
import com.gpay.payment_service.config.PaymentKafkaProperties;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.dto.TopupEventOutboxPayload;
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
 * Publishes top-up result events to Kafka and blocks until the broker
 * acknowledges, so the outbox row is only marked processed after a durable ack.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopupEventPublisher {

	private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

	private final KafkaTemplate<String, TopupEventOutboxPayload> topupEventKafkaTemplate;
	private final PaymentKafkaProperties properties;

	/**
	 * Sends a top-up result event and waits for the broker acknowledgement.
	 *
	 * @param eventType      succeeded or failed top-up event kind
	 * @param payload        top-up event payload
	 * @param idempotencyKey durable idempotency key carried as a record header
	 * @param traceId        trace id to propagate to the consumer
	 * @throws InterruptedException if the publishing thread is interrupted
	 * @throws ExecutionException   if the broker rejects the publish
	 */
	public void publish(
			OutboxEventType eventType,
			TopupEventOutboxPayload payload,
			String idempotencyKey,
			String traceId) throws InterruptedException, ExecutionException {
		String key = payload.walletId().toString();
		ProducerRecord<String, TopupEventOutboxPayload> record = new ProducerRecord<>(
				topicFor(eventType),
				key,
				payload);
		record.headers().add(IDEMPOTENCY_KEY_HEADER, idempotencyKey.getBytes(StandardCharsets.UTF_8));

		String previousTraceId = MDC.get(TraceIdContext.TRACE_ID_KEY);
		applyTraceId(traceId);
		KafkaTraceIdPropagation.injectTraceId(record.headers());

		try {
			SendResult<String, TopupEventOutboxPayload> result = topupEventKafkaTemplate.send(record).get();
			log.info(
					"Published top-up event type={} idempotencyKey={} partition={} offset={}",
					eventType,
					idempotencyKey,
					result.getRecordMetadata().partition(),
					result.getRecordMetadata().offset());
		} finally {
			restoreTraceId(previousTraceId);
		}
	}

	private String topicFor(OutboxEventType eventType) {
		if (eventType == OutboxEventType.TOPUP_SUCCEEDED) {
			return properties.topupSucceededTopic();
		}
		return properties.topupFailedTopic();
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
