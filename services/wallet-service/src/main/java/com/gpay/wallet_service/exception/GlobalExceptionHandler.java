package com.gpay.wallet_service.exception;

import com.gpay.common.tracing.TraceIdContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/* Maps wallet-service domain exceptions to API error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT = "uq_idempotency_keys_key";

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

	/**
	 * Maps a concurrent idempotency-key insert collision to {@code 409 Conflict}.
	 *
	 * <p>When two requests carrying the same idempotency key race past the initial lookup,
	 * the loser's insert violates the {@code uq_idempotency_keys_key} unique constraint. That
	 * is a transient write conflict the caller can resolve by retrying (the retry replays the
	 * stored response), so it surfaces as {@code 409} rather than a server error. Any other
	 * data-integrity violation signals a genuine defect and is rethrown to surface as {@code 500}.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		if (!isIdempotencyKeyCollision(ex)) {
			throw ex;
		}

		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(errorBody(
						"CONCURRENT_REQUEST_CONFLICT",
						"A concurrent request with the same idempotency key is in progress; please retry"));
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

	private boolean isIdempotencyKeyCollision(DataIntegrityViolationException ex) {
		String message = ex.getMostSpecificCause().getMessage();
		return message != null && message.contains(IDEMPOTENCY_KEY_UNIQUE_CONSTRAINT);
	}

	private Map<String, String> errorBody(String error, String message) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("error", error);
		body.put("message", message);
		body.put("trace_id", TraceIdContext.getTraceId());
		return body;
	}
}
