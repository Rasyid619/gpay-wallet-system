package com.gpay.payment_service.repository;

import com.gpay.payment_service.entity.TopupTransaction;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/* Data access for payment top-up transactions. */
public interface TopupTransactionRepository extends JpaRepository<TopupTransaction, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<TopupTransaction> findLockedById(UUID id);

	Optional<TopupTransaction> findByIdAndUserId(UUID id, UUID userId);
}
