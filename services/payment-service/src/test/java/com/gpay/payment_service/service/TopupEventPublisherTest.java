package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.common.tracing.TraceIdContext;
import com.gpay.payment_service.config.PaymentKafkaProperties;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.dto.TopupEventOutboxPayload;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for the top-up event Kafka publisher, exercising per-type topic
 * routing and the trace-id apply/restore branches without a running broker.
 */
class TopupEventPublisherTest {

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, TopupEventOutboxPayload> kafkaTemplate = mock(KafkaTemplate.class);

	private final PaymentKafkaProperties properties = new PaymentKafkaProperties(
			"wallet.credit.commands",
			"payment.topup.succeeded",
			"payment.topup.failed");
	private final TopupEventPublisher publisher = new TopupEventPublisher(kafkaTemplate, properties);

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	private TopupEventOutboxPayload payload() {
		return new TopupEventOutboxPayload(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				75_000L,
				null);
	}

	@SuppressWarnings("unchecked")
	private void stubSuccessfulSend() {
		ProducerRecord<String, TopupEventOutboxPayload> record = new ProducerRecord<>(
				"payment.topup.succeeded", "key", payload());
		RecordMetadata metadata =
				new RecordMetadata(new TopicPartition("payment.topup.succeeded", 0), 0L, 0, 0L, 0, 0);
		SendResult<String, TopupEventOutboxPayload> result = new SendResult<>(record, metadata);
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(result));
	}

	@SuppressWarnings("unchecked")
	private ProducerRecord<String, TopupEventOutboxPayload> capturedRecord() {
		ArgumentCaptor<ProducerRecord<String, TopupEventOutboxPayload>> captor =
				ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(captor.capture());
		return captor.getValue();
	}

	@Test
	void publishesSucceededEventToTopupSucceededTopic() throws Exception {
		stubSuccessfulSend();

		publisher.publish(OutboxEventType.TOPUP_SUCCEEDED, payload(), "idempotency-key", "trace");

		assertThat(capturedRecord().topic()).isEqualTo("payment.topup.succeeded");
	}

	@Test
	void publishesFailedEventToTopupFailedTopic() throws Exception {
		stubSuccessfulSend();

		publisher.publish(OutboxEventType.TOPUP_FAILED, payload(), "idempotency-key", "trace");

		assertThat(capturedRecord().topic()).isEqualTo("payment.topup.failed");
	}

	@Test
	void appliesProvidedTraceIdAndRemovesItWhenNoneWasPresent() throws Exception {
		stubSuccessfulSend();

		publisher.publish(OutboxEventType.TOPUP_SUCCEEDED, payload(), "idempotency-key", "trace-applied");

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isNull();
	}

	@Test
	void restoresPreviousTraceIdAfterPublish() throws Exception {
		stubSuccessfulSend();
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-previous");

		publisher.publish(OutboxEventType.TOPUP_SUCCEEDED, payload(), "idempotency-key", "trace-new");

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isEqualTo("trace-previous");
	}

	@Test
	void keepsExistingTraceIdWhenTraceIdArgumentIsBlank() throws Exception {
		stubSuccessfulSend();
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-existing");

		publisher.publish(OutboxEventType.TOPUP_SUCCEEDED, payload(), "idempotency-key", "  ");

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isEqualTo("trace-existing");
	}

	@Test
	void doesNotSetTraceIdWhenTraceIdArgumentIsNull() throws Exception {
		stubSuccessfulSend();

		publisher.publish(OutboxEventType.TOPUP_SUCCEEDED, payload(), "idempotency-key", null);

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isNull();
	}
}
