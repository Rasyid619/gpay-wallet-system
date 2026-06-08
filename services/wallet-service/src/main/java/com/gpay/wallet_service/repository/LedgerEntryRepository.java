package com.gpay.wallet_service.repository;

import com.gpay.wallet_service.entity.LedgerEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/* Database access for wallet mutation ledger rows. */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	/**
	 * Finds mutations belonging only to the authenticated user's wallet.
	 *
	 * @param userId   authenticated user identifier
	 * @param pageable pagination and deterministic sorting information
	 * @return paginated mutation rows
	 */
	@EntityGraph(attributePaths = "transfer")
	Page<LedgerEntry> findByWalletUserId(UUID userId, Pageable pageable);
}
