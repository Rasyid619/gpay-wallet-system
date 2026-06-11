package com.gpay.mock_gateway_service.exception;

import com.gpay.common.tracing.TraceIdContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(fe -> fe.getField() + " " + fe.getDefaultMessage())
				.findFirst()
				.orElse("Invalid request");
		return ResponseEntity.badRequest()
				.body(errorBody("VALIDATION_ERROR", message));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> handleUnreadableRequest(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest()
				.body(errorBody("VALIDATION_ERROR", "Invalid request body"));
	}

	private Map<String, String> errorBody(String error, String message) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("error", error);
		body.put("message", message);
		body.put("trace_id", TraceIdContext.getTraceId());
		return body;
	}
}
