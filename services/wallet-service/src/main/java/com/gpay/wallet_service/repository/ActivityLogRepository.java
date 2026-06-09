package com.gpay.wallet_service.repository;

import com.gpay.wallet_service.entity.ActivityLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/* Database access for wallet activity logs. */
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

	/**
	 * Counts activity logs associated with a wallet.
	 *
	 * @param walletId wallet identifier
	 * @return number of logs for the wallet
	 */
	long countByWalletId(UUID walletId);
}
