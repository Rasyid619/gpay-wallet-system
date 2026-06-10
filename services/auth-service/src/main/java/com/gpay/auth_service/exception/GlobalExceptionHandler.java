package com.gpay.auth_service.exception;

import com.gpay.auth_service.config.TraceIdContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/* Maps domain exceptions to HTTP error responses without exposing stack traces. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(errorBody("UNAUTHORIZED", ex.getMessage()));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(errorBody("EMAIL_ALREADY_REGISTERED", ex.getMessage()));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(errorBody("NOT_FOUND", ex.getMessage()));
	}

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

	private Map<String, String> errorBody(String error, String message) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("error", error);
		body.put("message", message);
		body.put("trace_id", TraceIdContext.getTraceId());
		return body;
	}
}
