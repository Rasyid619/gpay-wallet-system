package com.gpay.auth_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Unit tests for trace-aware Spring Security unauthorized responses.
 */
class TraceIdAuthenticationEntryPointTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final TraceIdAuthenticationEntryPoint entryPoint = new TraceIdAuthenticationEntryPoint(objectMapper);

	@Test
	void returnsTraceIdInUnauthorizedBody() throws IOException {
		MockHttpServletResponse response = new MockHttpServletResponse();
		MDC.put(TraceIdContext.TRACE_ID_KEY, "trace-auth-security");

		try {
			entryPoint.commence(
					new MockHttpServletRequest(),
					response,
					new BadCredentialsException("Invalid token"));
		} finally {
			MDC.remove(TraceIdContext.TRACE_ID_KEY);
		}

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(body.get("error").asText()).isEqualTo("UNAUTHORIZED");
		assertThat(body.get("trace_id").asText()).isEqualTo("trace-auth-security");
	}
}
