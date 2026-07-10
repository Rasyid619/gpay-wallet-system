package com.gpay.notification_service.repository;

import com.gpay.notification_service.entity.NotificationAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/* Data access for persisted notification delivery attempts. */
public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, UUID> {

	Optional<NotificationAttempt> findByEventId(UUID eventId);
}
