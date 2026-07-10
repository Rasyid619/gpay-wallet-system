package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.common.tracing.TraceIdContext;
import com.gpay.wallet_service.config.WalletKafkaProperties;
import com.gpay.wallet_service.constant.OutboxEventType;
import com.gpay.wallet_service.dto.TransferEventOutboxPayload;
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
 * Unit tests for the transfer-event Kafka publisher, exercising per-type topic
 * routing and the trace-id apply/restore branches without a running broker.
 */
class TransferEventPublisherTest {

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, TransferEventOutboxPayload> kafkaTemplate = mock(KafkaTemplate.class);

	private final WalletKafkaProperties properties = new WalletKafkaProperties(
			"wallet.credit.commands",
			"dead-letter.events",
			"wallet.transfer.completed",
			"wallet.transfer.received",
			"wallet.transfer.failed");
	private final TransferEventPublisher publisher = new TransferEventPublisher(kafkaTemplate, properties);

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	private TransferEventOutboxPayload payload() {
		return new TransferEventOutboxPayload(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				75_000L,
				null);
	}

	@SuppressWarnings("unchecked")
	private void stubSuccessfulSend() {
		ProducerRecord<String, TransferEventOutboxPayload> record = new ProducerRecord<>(
				"wallet.transfer.completed", "key", payload());
		RecordMetadata metadata =
				new RecordMetadata(new TopicPartition("wallet.transfer.completed", 0), 0L, 0, 0L, 0, 0);
		SendResult<String, TransferEventOutboxPayload> result = new SendResult<>(record, metadata);
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(result));
	}

	@SuppressWarnings("unchecked")
	private ProducerRecord<String, TransferEventOutboxPayload> capturedRecord() {
		ArgumentCaptor<ProducerRecord<String, TransferEventOutboxPayload>> captor =
				ArgumentCaptor.forClass(ProducerRecord.class);
		verify(kafkaTemplate).send(captor.capture());
		return captor.getValue();
	}

	@Test
	void publishesCompletedEventToTransferCompletedTopic() throws Exception {
		stubSuccessfulSend();

		publisher.publish(OutboxEventType.TRANSFER_COMPLETED, payload(), "idempotency-key", "trace");

		assertThat(capturedRecord().topic()).isEqualTo("wallet.transfer.completed");
	}

	@Test
	void publishesReceivedEventToTransferReceivedTopic() throws Exception {
		stubSuccessfulSend();

		publisher.publish(OutboxEventType.TRANSFER_RECEIVED, payload(), "idempotency-key", "trace");

		assertThat(capturedRecord().topic()).isEqualTo("wallet.transfer.received");
	}

	@Test
	void publishesFailedEventToTransferFailedTopic() throws Exception {
		stubSuccessfulSend();

		publisher.publish(OutboxEventType.TRANSFER_FAILED, payload(), "idempotency-key", "trace");

		assertThat(capturedRecord().topic()).isEqualTo("wallet.transfer.failed");
	}

	@Test
	void appliesProvidedTraceIdAndRemovesItWhenNoneWasPresent() throws Exception {
		stubSuccessfulSend();

		publisher.publish(OutboxEventType.TRANSFER_COMPLETED, payload(), "idempotency-key", "trace-applied");

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isNull();
	}

	@Test
	void restoresPreviousTraceIdAfterPublish() throws Exception {
		stubSuccessfulSend();
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-previous");

		publisher.publish(OutboxEventType.TRANSFER_COMPLETED, payload(), "idempotency-key", "trace-new");

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isEqualTo("trace-previous");
	}

	@Test
	void keepsExistingTraceIdWhenTraceIdArgumentIsBlank() throws Exception {
		stubSuccessfulSend();
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-existing");

		publisher.publish(OutboxEventType.TRANSFER_COMPLETED, payload(), "idempotency-key", "  ");

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isEqualTo("trace-existing");
	}

	@Test
	void doesNotSetTraceIdWhenTraceIdArgumentIsNull() throws Exception {
		stubSuccessfulSend();

		publisher.publish(OutboxEventType.TRANSFER_COMPLETED, payload(), "idempotency-key", null);

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isNull();
	}
}
