package com.gpay.wallet_service.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for the data-integrity branch of the wallet-service exception handler, covering
 * the concurrent idempotency-key collision mapping and the rethrow of unrelated violations.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsIdempotencyKeyCollisionToConflict() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException(
				"insert failed",
				new RuntimeException("duplicate key value violates unique constraint \"uq_idempotency_keys_key\""));

		ResponseEntity<Map<String, String>> response = handler.handleDataIntegrityViolation(ex);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).containsEntry("error", "CONCURRENT_REQUEST_CONFLICT");
	}

	@Test
	void rethrowsUnrelatedDataIntegrityViolation() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException(
				"insert failed",
				new RuntimeException("null value in column \"amount\" violates not-null constraint"));

		assertThatThrownBy(() -> handler.handleDataIntegrityViolation(ex))
				.isSameAs(ex);
	}

	@Test
	void rethrowsWhenCauseHasNoMessage() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("insert failed", new RuntimeException());

		assertThatThrownBy(() -> handler.handleDataIntegrityViolation(ex))
				.isSameAs(ex);
	}
}
