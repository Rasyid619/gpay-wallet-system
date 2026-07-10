package com.gpay.wallet_service.repository;

import com.gpay.wallet_service.constant.OutboxEventStatus;
import com.gpay.wallet_service.constant.OutboxEventType;
import com.gpay.wallet_service.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/* Data access for durable wallet outbox events. */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

	boolean existsByAggregateIdAndEventType(UUID aggregateId, OutboxEventType eventType);

	@Query("""
			SELECT event
			FROM OutboxEvent event
			WHERE event.status = :status
				AND (event.nextRetryAt IS NULL OR event.nextRetryAt <= :now)
			ORDER BY event.createdAt ASC
			""")
	List<OutboxEvent> findDueEvents(
			OutboxEventStatus status,
			Instant now,
			Pageable pageable);

	@Query("""
			SELECT event
			FROM OutboxEvent event
			WHERE event.status = :status
				AND event.updatedAt <= :staleBefore
			ORDER BY event.updatedAt ASC
			""")
	List<OutboxEvent> findStaleProcessingEvents(
			OutboxEventStatus status,
			Instant staleBefore,
			Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OutboxEvent> findLockedById(UUID id);
}
