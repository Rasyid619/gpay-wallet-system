package com.gpay.payment_service.repository;

import com.gpay.payment_service.entity.IdempotencyKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/* Data access for payment idempotency response records. */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

	Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
