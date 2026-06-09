package com.gpay.wallet_service.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/* Maps wallet-service domain exceptions to API error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(WalletNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleWalletNotFound(WalletNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("error", "WALLET_NOT_FOUND", "message", ex.getMessage()));
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("error", "IDEMPOTENCY_KEY_CONFLICT", "message", ex.getMessage()));
	}

	@ExceptionHandler(PaymentTransactionConflictException.class)
	public ResponseEntity<Map<String, String>> handlePaymentTransactionConflict(PaymentTransactionConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("error", "PAYMENT_TRANSACTION_CONFLICT", "message", ex.getMessage()));
	}

	@ExceptionHandler(InternalAuthenticationException.class)
	public ResponseEntity<Map<String, String>> handleInternalAuthentication(InternalAuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "UNAUTHORIZED", "message", ex.getMessage()));
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
