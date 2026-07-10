package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gpay.common.tracing.TraceIdContext;
import com.gpay.payment_service.config.PaymentKafkaProperties;
import com.gpay.payment_service.dto.WalletCreditOutboxPayload;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for the wallet-credit Kafka publisher, exercising the trace-id apply/restore
 * branches around the broker acknowledgement without a running broker.
 */
class WalletCreditCommandPublisherTest {

	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, WalletCreditOutboxPayload> kafkaTemplate = mock(KafkaTemplate.class);

	private final PaymentKafkaProperties properties = new PaymentKafkaProperties(
			"wallet.credit.commands",
			"payment.topup.succeeded",
			"payment.topup.failed");
	private final WalletCreditCommandPublisher publisher =
			new WalletCreditCommandPublisher(kafkaTemplate, properties);

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	private WalletCreditOutboxPayload payload() {
		return new WalletCreditOutboxPayload(UUID.randomUUID(), UUID.randomUUID(), 75_000L);
	}

	@SuppressWarnings("unchecked")
	private void stubSuccessfulSend() {
		ProducerRecord<String, WalletCreditOutboxPayload> record = new ProducerRecord<>(
				"wallet.credit.commands", "key", payload());
		RecordMetadata metadata =
				new RecordMetadata(new TopicPartition("wallet.credit.commands", 0), 0L, 0, 0L, 0, 0);
		SendResult<String, WalletCreditOutboxPayload> result = new SendResult<>(record, metadata);
		when(kafkaTemplate.send(any(ProducerRecord.class)))
				.thenReturn(CompletableFuture.completedFuture(result));
	}

	@Test
	void appliesProvidedTraceIdAndRemovesItWhenNoneWasPresent() throws Exception {
		stubSuccessfulSend();

		publisher.publish(payload(), "idempotency-key", "trace-applied");

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isNull();
	}

	@Test
	void restoresPreviousTraceIdAfterPublish() throws Exception {
		stubSuccessfulSend();
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-previous");

		publisher.publish(payload(), "idempotency-key", "trace-new");

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isEqualTo("trace-previous");
	}

	@Test
	void keepsExistingTraceIdWhenTraceIdArgumentIsBlank() throws Exception {
		stubSuccessfulSend();
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-existing");

		publisher.publish(payload(), "idempotency-key", "  ");

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isEqualTo("trace-existing");
	}

	@Test
	void doesNotSetTraceIdWhenTraceIdArgumentIsNull() throws Exception {
		stubSuccessfulSend();

		publisher.publish(payload(), "idempotency-key", null);

		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isNull();
	}
}
