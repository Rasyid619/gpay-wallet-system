package com.gpay.payment_service.repository;

import com.gpay.payment_service.entity.TopupTransaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/* Data access for payment top-up transactions. */
public interface TopupTransactionRepository extends JpaRepository<TopupTransaction, UUID> {
}
