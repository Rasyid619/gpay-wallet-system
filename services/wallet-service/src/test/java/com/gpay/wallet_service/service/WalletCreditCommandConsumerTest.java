package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.common.tracing.KafkaTraceIdPropagation;
import com.gpay.wallet_service.dto.IdempotentResponse;
import com.gpay.wallet_service.dto.InternalWalletCreditRequest;
import com.gpay.wallet_service.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the Kafka wallet credit command consumer, covering successful
 * delegation, idempotent duplicate handling, and non-retryable payload failures.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletCreditCommandConsumerTest {

	private static final String INTERNAL_TOKEN = "internal-test-token";

	@Mock
	private InternalWalletCreditService internalWalletCreditService;

	@InjectMocks
	private WalletCreditCommandConsumer consumer;

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void delegatesCreditWithIdempotencyKeyAndRestoredTraceId() {
		ReflectionTestUtils.setField(consumer, "internalToken", INTERNAL_TOKEN);
		InternalWalletCreditRequest request = creditRequest();
		ConsumerRecord<String, InternalWalletCreditRequest> record =
				record(request, "payment-outbox-abc", "trace-from-payment");
		when(internalWalletCreditService.credit(eq(INTERNAL_TOKEN), eq("payment-outbox-abc"), eq(request), eq("trace-from-payment")))
				.thenReturn(new IdempotentResponse(200, "ok"));

		consumer.onWalletCreditCommand(record);

		ArgumentCaptor<String> traceCaptor = ArgumentCaptor.forClass(String.class);
		verify(internalWalletCreditService).credit(eq(INTERNAL_TOKEN), eq("payment-outbox-abc"), eq(request), traceCaptor.capture());
		assertThat(traceCaptor.getValue()).isEqualTo("trace-from-payment");
	}

	@Test
	void duplicateDeliveryStillDelegatesToIdempotentCredit() {
		ReflectionTestUtils.setField(consumer, "internalToken", INTERNAL_TOKEN);
		InternalWalletCreditRequest request = creditRequest();
		ConsumerRecord<String, InternalWalletCreditRequest> record =
				record(request, "payment-outbox-dup", "trace-dup");
		when(internalWalletCreditService.credit(eq(INTERNAL_TOKEN), eq("payment-outbox-dup"), eq(request), eq("trace-dup")))
				.thenReturn(new IdempotentResponse(200, "replayed"));

		consumer.onWalletCreditCommand(record);
		consumer.onWalletCreditCommand(record);

		verify(internalWalletCreditService, times(2))
				.credit(eq(INTERNAL_TOKEN), eq("payment-outbox-dup"), eq(request), eq("trace-dup"));
	}

	@Test
	void throwsWhenPayloadMissingSoErrorHandlerCanDeadLetter() {
		ReflectionTestUtils.setField(consumer, "internalToken", INTERNAL_TOKEN);
		ConsumerRecord<String, InternalWalletCreditRequest> record =
				record(null, "payment-outbox-null", "trace-null");

		assertThatThrownBy(() -> consumer.onWalletCreditCommand(record))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void throwsWhenIdempotencyKeyHeaderMissing() {
		ReflectionTestUtils.setField(consumer, "internalToken", INTERNAL_TOKEN);
		ConsumerRecord<String, InternalWalletCreditRequest> record =
				new ConsumerRecord<>("wallet.credit.commands", 0, 0L, "key", creditRequest());

		assertThatThrownBy(() -> consumer.onWalletCreditCommand(record))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void throwsWhenIdempotencyKeyHeaderValueIsNull() {
		ReflectionTestUtils.setField(consumer, "internalToken", INTERNAL_TOKEN);
		ConsumerRecord<String, InternalWalletCreditRequest> record =
				new ConsumerRecord<>("wallet.credit.commands", 0, 0L, "key", creditRequest());
		record.headers().add("Idempotency-Key", null);

		assertThatThrownBy(() -> consumer.onWalletCreditCommand(record))
				.isInstanceOf(BadRequestException.class);
	}

	private InternalWalletCreditRequest creditRequest() {
		return new InternalWalletCreditRequest(UUID.randomUUID(), UUID.randomUUID(), 50000L);
	}

	private ConsumerRecord<String, InternalWalletCreditRequest> record(
			InternalWalletCreditRequest value, String idempotencyKey, String traceId) {
		ConsumerRecord<String, InternalWalletCreditRequest> record =
				new ConsumerRecord<>("wallet.credit.commands", 0, 0L, "key", value);
		Headers headers = record.headers();
		headers.add("Idempotency-Key", idempotencyKey.getBytes(StandardCharsets.UTF_8));
		headers.add(KafkaTraceIdPropagation.TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
		return record;
	}
}
