package com.gpay.wallet_service.repository;

import com.gpay.wallet_service.entity.Wallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/* Database access for wallet balance rows. */
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

	/**
	 * Finds the wallet owned by an auth-service user.
	 *
	 * @param userId authenticated user identifier
	 * @return wallet row when it exists
	 */
	Optional<Wallet> findByUserId(UUID userId);
}
