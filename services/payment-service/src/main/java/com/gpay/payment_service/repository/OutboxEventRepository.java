package com.gpay.payment_service.repository;

import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.entity.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

	boolean existsByAggregateIdAndEventType(UUID aggregateId, OutboxEventType eventType);
}
