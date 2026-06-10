package com.gpay.auth_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for request trace id generation and response exposure.
 */
class TraceIdFilterTest {

	private final TraceIdFilter traceIdFilter = new TraceIdFilter();

	@Test
	void preservesIncomingTraceId() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-auth");

		traceIdFilter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo("trace-auth");
		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isNull();
	}

	@Test
	void generatesTraceIdWhenMissing() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		traceIdFilter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isNotBlank();
		assertThat(MDC.get(TraceIdContext.TRACE_ID_KEY)).isNull();
	}
}
