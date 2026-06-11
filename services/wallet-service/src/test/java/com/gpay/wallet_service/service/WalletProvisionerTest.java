package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for lazy wallet provisioning.
 */
@ExtendWith(MockitoExtension.class)
class WalletProvisionerTest {

	@Mock
	private WalletRepository walletRepository;

	private WalletProvisioner walletProvisioner;

	@BeforeEach
	void setUp() {
		walletProvisioner = new WalletProvisioner(walletRepository);
	}

	@Test
	void returnsExistingWalletWithoutCreatingANewOne() {
		UUID userId = UUID.randomUUID();
		Wallet existing = Wallet.create(
				UUID.randomUUID(), userId, 5000L, WalletStatus.ACTIVE, Instant.now(), Instant.now());
		when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

		Wallet result = walletProvisioner.getOrProvision(userId);

		assertThat(result).isSameAs(existing);
		verify(walletRepository, never()).saveAndFlush(any(Wallet.class));
	}

	@Test
	void provisionsZeroBalanceActiveWalletWhenMissing() {
		UUID userId = UUID.randomUUID();
		when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
		when(walletRepository.saveAndFlush(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

		Wallet result = walletProvisioner.getOrProvision(userId);

		ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
		verify(walletRepository).saveAndFlush(captor.capture());
		Wallet saved = captor.getValue();
		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.getBalance()).isZero();
		assertThat(saved.getStatus()).isEqualTo(WalletStatus.ACTIVE);
		assertThat(result).isSameAs(saved);
	}
}
