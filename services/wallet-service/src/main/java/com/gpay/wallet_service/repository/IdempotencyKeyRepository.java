package com.gpay.wallet_service.repository;

import com.gpay.wallet_service.entity.IdempotencyKey;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/* Database access for durable wallet idempotency records. */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

	/**
	 * Finds an idempotency record while locking it for duplicate request replay.
	 *
	 * @param idempotencyKey client-provided idempotency key
	 * @return stored idempotency response when available
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);

	/**
	 * Checks whether an idempotency key exists without taking a lock.
	 *
	 * @param idempotencyKey client-provided idempotency key
	 * @return true when the key exists
	 */
	boolean existsByIdempotencyKey(String idempotencyKey);
}
