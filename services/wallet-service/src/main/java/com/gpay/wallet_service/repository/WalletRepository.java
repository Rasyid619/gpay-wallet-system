package com.gpay.wallet_service.repository;

import com.gpay.wallet_service.entity.Wallet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* Database access for wallet balance rows. */
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

	/**
	 * Finds the wallet owned by an auth-service user.
	 *
	 * @param userId authenticated user identifier
	 * @return wallet row when it exists
	 */
	Optional<Wallet> findByUserId(UUID userId);

	/**
	 * Locks wallet rows in deterministic UUID order for balance-changing workflows.
	 *
	 * @param walletIds wallet identifiers to lock
	 * @return locked wallet rows ordered by wallet identifier
	 */
	@Query(value = """
			SELECT id, user_id, balance, status, created_at, updated_at
			FROM wallets
			WHERE id IN (:walletIds)
			ORDER BY id
			FOR UPDATE
			""", nativeQuery = true)
	List<Wallet> findAllByIdInForUpdate(@Param("walletIds") List<UUID> walletIds);

	/**
	 * Locks one wallet row for a balance-changing workflow.
	 *
	 * @param walletId wallet identifier to lock
	 * @return locked wallet row when it exists
	 */
	@Query(value = """
			SELECT id, user_id, balance, status, created_at, updated_at
			FROM wallets
			WHERE id = :walletId
			FOR UPDATE
			""", nativeQuery = true)
	Optional<Wallet> findByIdForUpdate(@Param("walletId") UUID walletId);
}
