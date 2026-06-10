package com.gpay.mock_gateway_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/* Ensures every request has an X-Trace-Id and stores it in MDC for logs. */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String TRACE_ID_HEADER = "X-Trace-Id";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String traceId = resolveTraceId(request);
		MDC.put(TraceIdContext.TRACE_ID_KEY, traceId);
		response.setHeader(TRACE_ID_HEADER, traceId);

		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(TraceIdContext.TRACE_ID_KEY);
		}
	}

	private String resolveTraceId(HttpServletRequest request) {
		String traceId = request.getHeader(TRACE_ID_HEADER);
		if (StringUtils.hasText(traceId)) {
			return traceId;
		}
		return UUID.randomUUID().toString();
	}
}
