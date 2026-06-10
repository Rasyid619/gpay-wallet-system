package com.gpay.wallet_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.StringUtils;

/* Writes trace-aware JSON responses for Spring Security authentication failures. */
@RequiredArgsConstructor
public class TraceIdAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private static final String TRACE_ID_HEADER = "X-Trace-Id";

	private final ObjectMapper objectMapper;

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		String traceId = resolveTraceId(request);
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setHeader(TRACE_ID_HEADER, traceId);
		objectMapper.writeValue(response.getOutputStream(), errorBody(traceId));
	}

	private String resolveTraceId(HttpServletRequest request) {
		String traceId = TraceIdContext.getTraceId();
		if (StringUtils.hasText(traceId)) {
			return traceId;
		}

		traceId = request.getHeader(TRACE_ID_HEADER);
		if (StringUtils.hasText(traceId)) {
			return traceId;
		}

		return UUID.randomUUID().toString();
	}

	private Map<String, String> errorBody(String traceId) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("error", "UNAUTHORIZED");
		body.put("message", "Authentication is required");
		body.put("trace_id", traceId);
		return body;
	}
}
