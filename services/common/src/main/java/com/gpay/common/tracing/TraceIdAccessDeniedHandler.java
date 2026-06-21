package com.gpay.common.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.StringUtils;

/* Writes trace-aware JSON responses for Spring Security authorization (403) failures. */
public class TraceIdAccessDeniedHandler implements AccessDeniedHandler {

	private static final String TRACE_ID_HEADER = "X-Trace-Id";

	private final ObjectMapper objectMapper;

	public TraceIdAccessDeniedHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		String traceId = resolveTraceId(request);
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
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
		body.put("error", "FORBIDDEN");
		body.put("message", "Access is denied");
		body.put("trace_id", traceId);
		return body;
	}
}
