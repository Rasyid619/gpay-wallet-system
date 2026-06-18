package com.gpay.common.tracing;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

/**
 * Propagates the request trace id across Kafka messages.
 *
 * <p>Producers inject the current MDC trace id (generating one when absent) into
 * a Kafka record header so it travels with the message. Consumers restore that
 * header value into MDC before handling, again generating one when the header is
 * missing, so producer and consumer logs share a single trace id.
 */
public final class KafkaTraceIdPropagation {

	public static final String TRACE_ID_HEADER = "X-Trace-Id";

	private KafkaTraceIdPropagation() {
	}

	/**
	 * Writes the current MDC trace id into the given record headers, generating a
	 * new trace id and placing it in MDC when none is present.
	 *
	 * @param headers outbound Kafka record headers to enrich
	 * @return the trace id written to the headers
	 */
	public static String injectTraceId(Headers headers) {
		String traceId = MDC.get(TraceIdContext.TRACE_ID_KEY);
		if (traceId == null || traceId.isBlank()) {
			traceId = UUID.randomUUID().toString();
			MDC.put(TraceIdContext.TRACE_ID_KEY, traceId);
		}

		headers.remove(TRACE_ID_HEADER);
		headers.add(TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
		return traceId;
	}

	/**
	 * Restores the trace id from the given record headers into MDC, generating a
	 * new trace id when the header is missing or blank.
	 *
	 * @param headers inbound Kafka record headers to read
	 * @return the trace id placed in MDC
	 */
	public static String restoreTraceId(Headers headers) {
		String traceId = readTraceId(headers);
		if (traceId == null || traceId.isBlank()) {
			traceId = UUID.randomUUID().toString();
		}

		MDC.put(TraceIdContext.TRACE_ID_KEY, traceId);
		return traceId;
	}

	/** Removes the trace id from MDC once consumer handling completes. */
	public static void clearTraceId() {
		MDC.remove(TraceIdContext.TRACE_ID_KEY);
	}

	private static String readTraceId(Headers headers) {
		Header header = headers.lastHeader(TRACE_ID_HEADER);
		if (header == null || header.value() == null) {
			return null;
		}
		return new String(header.value(), StandardCharsets.UTF_8);
	}
}
