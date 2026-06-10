package com.gpay.wallet_service.exception;

import com.gpay.wallet_service.config.TraceIdContext;
import java.util.LinkedHashMap;
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
				.body(errorBody("WALLET_NOT_FOUND", ex.getMessage()));
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(errorBody("IDEMPOTENCY_KEY_CONFLICT", ex.getMessage()));
	}

	@ExceptionHandler(PaymentTransactionConflictException.class)
	public ResponseEntity<Map<String, String>> handlePaymentTransactionConflict(PaymentTransactionConflictException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(errorBody("PAYMENT_TRANSACTION_CONFLICT", ex.getMessage()));
	}

	@ExceptionHandler(InternalAuthenticationException.class)
	public ResponseEntity<Map<String, String>> handleInternalAuthentication(InternalAuthenticationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(errorBody("UNAUTHORIZED", ex.getMessage()));
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
		return ResponseEntity.badRequest()
				.body(errorBody("VALIDATION_ERROR", ex.getMessage()));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<Map<String, String>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
		return ResponseEntity.badRequest()
				.body(errorBody("VALIDATION_ERROR", ex.getHeaderName() + " header is required"));
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
