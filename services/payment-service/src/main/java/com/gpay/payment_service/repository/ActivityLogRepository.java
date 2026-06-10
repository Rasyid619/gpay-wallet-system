package com.gpay.payment_service.repository;

import com.gpay.payment_service.entity.ActivityLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/* Database access for payment activity logs. */
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

	/**
	 * Lists activity logs for a payment transaction in creation order.
	 *
	 * @param transactionId payment transaction identifier
	 * @return transaction activity logs
	 */
	List<ActivityLog> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
