package com.gpay.wallet_service.repository;

import com.gpay.wallet_service.entity.Transfer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/* Database access for wallet-to-wallet transfer records. */
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

	/**
	 * Sums successful transfer amount sent by a wallet in a time window.
	 *
	 * @param senderWalletId sender wallet identifier
	 * @param from           inclusive lower time bound
	 * @param to             exclusive upper time bound
	 * @return total sent amount in whole IDR
	 */
	@Query(value = """
			SELECT COALESCE(SUM(amount), 0)
			FROM transfers
			WHERE sender_wallet_id = :senderWalletId
			  AND status = 'SUCCESS'::transfer_status
			  AND created_at >= :from
			  AND created_at < :to
			""", nativeQuery = true)
	Long sumSuccessfulAmountBySenderWalletIdBetween(
			@Param("senderWalletId") UUID senderWalletId,
			@Param("from") Instant from,
			@Param("to") Instant to);
}
