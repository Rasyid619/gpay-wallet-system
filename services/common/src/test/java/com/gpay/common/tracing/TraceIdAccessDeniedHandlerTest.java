package com.gpay.common.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

/* Unit tests for trace-aware Spring Security forbidden responses. */
class TraceIdAccessDeniedHandlerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final TraceIdAccessDeniedHandler handler = new TraceIdAccessDeniedHandler(objectMapper);

	@Test
	void usesMdcTraceIdInForbiddenBody() throws IOException {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-from-mdc");

		try {
			handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));
		} finally {
			MDC.remove(TraceIdContext.TRACE_ID_KEY);
		}

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(body.get("error").asText()).isEqualTo("FORBIDDEN");
		assertThat(body.get("trace_id").asText()).isEqualTo("trace-from-mdc");
	}

	@Test
	void fallsBackToTraceIdHeaderWhenMdcIsEmpty() throws IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Trace-Id", "trace-from-header");
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.handle(request, response, new AccessDeniedException("denied"));

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.get("trace_id").asText()).isEqualTo("trace-from-header");
		assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-from-header");
	}

	@Test
	void generatesTraceIdWhenNoneSupplied() throws IOException {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.get("trace_id").asText()).isNotBlank();
	}
}
