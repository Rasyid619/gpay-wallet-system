package com.gpay.common.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Unit tests for trace id propagation between Kafka producers and consumers.
 */
class KafkaTraceIdPropagationTest {

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	void injectsTraceIdFromMdcIntoHeaders() {
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-producer");
		Headers headers = new RecordHeaders();

		String injected = KafkaTraceIdPropagation.injectTraceId(headers);

		assertThat(injected).isEqualTo("trace-producer");
		assertThat(headerValue(headers)).isEqualTo("trace-producer");
	}

	@Test
	void generatesTraceIdForHeadersWhenMdcEmpty() {
		Headers headers = new RecordHeaders();

		String injected = KafkaTraceIdPropagation.injectTraceId(headers);

		assertThat(injected).isNotBlank();
		assertThat(headerValue(headers)).isEqualTo(injected);
		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isEqualTo(injected);
	}

	@Test
	void restoresTraceIdFromHeadersIntoMdc() {
		Headers headers = new RecordHeaders();
		headers.add(
				KafkaTraceIdPropagation.TRACE_ID_HEADER,
				"trace-consumer".getBytes(StandardCharsets.UTF_8));

		String restored = KafkaTraceIdPropagation.restoreTraceId(headers);

		assertThat(restored).isEqualTo("trace-consumer");
		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isEqualTo("trace-consumer");
	}

	@Test
	void generatesTraceIdInMdcWhenHeaderMissing() {
		Headers headers = new RecordHeaders();

		String restored = KafkaTraceIdPropagation.restoreTraceId(headers);

		assertThat(restored).isNotBlank();
		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isEqualTo(restored);
	}

	private String headerValue(Headers headers) {
		Header header = headers.lastHeader(KafkaTraceIdPropagation.TRACE_ID_HEADER);
		return new String(header.value(), StandardCharsets.UTF_8);
	}
}
