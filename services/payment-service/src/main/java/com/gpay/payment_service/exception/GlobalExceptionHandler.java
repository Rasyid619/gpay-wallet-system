package com.gpay.payment_service.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/* Maps payment-service domain exceptions to API error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<Map<String, String>> handleRateLimitExceeded(RateLimitExceededException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(Map.of("error", "RATE_LIMIT_EXCEEDED", "message", ex.getMessage()));
	}

	@ExceptionHandler(RateLimitUnavailableException.class)
	public ResponseEntity<Map<String, String>> handleRateLimitUnavailable(RateLimitUnavailableException ex) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of("error", "RATE_LIMIT_UNAVAILABLE", "message", ex.getMessage()));
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
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
