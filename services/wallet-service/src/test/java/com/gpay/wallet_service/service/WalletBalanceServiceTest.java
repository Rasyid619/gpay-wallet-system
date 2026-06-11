package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.entity.Wallet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for authenticated wallet balance lookup.
 */
@ExtendWith(MockitoExtension.class)
class WalletBalanceServiceTest {

	@Mock
	private WalletProvisioner walletProvisioner;

	private WalletBalanceService walletBalanceService;

	@BeforeEach
	void setUp() {
		walletBalanceService = new WalletBalanceService(walletProvisioner);
	}

	@Nested
	class GetBalance {

		@Test
		void returnsWalletBalanceForUser() {
			UUID userId = UUID.randomUUID();
			UUID walletId = UUID.randomUUID();
			Wallet wallet = Wallet.create(walletId, userId, 87500L, WalletStatus.ACTIVE, Instant.now(), Instant.now());
			when(walletProvisioner.getOrProvision(userId)).thenReturn(wallet);

			WalletBalanceResponse response = walletBalanceService.getBalance(userId);

			assertThat(response.walletId()).isEqualTo(walletId);
			assertThat(response.balance()).isEqualTo(87500L);
		}

		@Test
		void returnsZeroBalanceWhenWalletIsProvisionedOnFirstAccess() {
			UUID userId = UUID.randomUUID();
			UUID walletId = UUID.randomUUID();
			Wallet provisioned = Wallet.create(walletId, userId, 0L, WalletStatus.ACTIVE, Instant.now(), Instant.now());
			when(walletProvisioner.getOrProvision(userId)).thenReturn(provisioned);

			WalletBalanceResponse response = walletBalanceService.getBalance(userId);

			assertThat(response.walletId()).isEqualTo(walletId);
			assertThat(response.balance()).isZero();
		}
	}
}
