package com.gpay.payment_service.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/* Maps payment-service domain exceptions to API error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyConflictException ex) {
		return ResponseEntity.status(409)
				.body(Map.of("error", "IDEMPOTENCY_KEY_CONFLICT", "message", ex.getMessage()));
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
		return ResponseEntity.badRequest()
				.body(Map.of("error", "VALIDATION_ERROR", "message", ex.getMessage()));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<Map<String, String>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
		return ResponseEntity.badRequest()
				.body(Map.of("error", "VALIDATION_ERROR", "message", ex.getHeaderName() + " header is required"));
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
				.body(Map.of("error", "VALIDATION_ERROR", "message", message));
	}
}
