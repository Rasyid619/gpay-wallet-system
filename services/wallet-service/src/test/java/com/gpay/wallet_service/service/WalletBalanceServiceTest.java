package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.exception.WalletNotFoundException;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.Optional;
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

	@Mock
	private WalletRepository walletRepository;

	private WalletBalanceService walletBalanceService;

	@BeforeEach
	void setUp() {
		walletBalanceService = new WalletBalanceService(walletProvisioner, walletRepository);
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

	@Nested
	class GetWalletById {

		@Test
		void returnsWalletBalanceForAnyOwner() {
			UUID walletId = UUID.randomUUID();
			Wallet wallet = Wallet.create(
					walletId, UUID.randomUUID(), 175_000L, WalletStatus.ACTIVE, Instant.now(), Instant.now());
			when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

			WalletBalanceResponse response = walletBalanceService.getWalletById(walletId);

			assertThat(response.walletId()).isEqualTo(walletId);
			assertThat(response.balance()).isEqualTo(175_000L);
		}

		@Test
		void throwsWalletNotFoundWhenWalletMissing() {
			UUID walletId = UUID.randomUUID();
			when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> walletBalanceService.getWalletById(walletId))
					.isInstanceOf(WalletNotFoundException.class);
		}
	}
}
