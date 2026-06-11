package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gpay.wallet_service.dto.InternalWalletProvisionRequest;
import com.gpay.wallet_service.dto.InternalWalletProvisionResponse;
import com.gpay.wallet_service.exception.InternalAuthenticationException;
import com.gpay.wallet_service.repository.WalletRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for internal wallet provisioning behavior.
 */
@SpringBootTest(properties = "wallet.internal-token=internal-test-token")
class InternalWalletProvisionServiceTest {

	@Autowired
	private InternalWalletProvisionService internalWalletProvisionService;

	@Autowired
	private WalletRepository walletRepository;

	@Test
	void provisionsZeroBalanceWalletForUser() {
		UUID userId = UUID.randomUUID();

		InternalWalletProvisionResponse response = internalWalletProvisionService.provision(
				"internal-test-token",
				new InternalWalletProvisionRequest(userId));

		assertThat(response.walletId()).isNotNull();
		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.balance()).isZero();
		assertThat(response.status()).isEqualTo("ACTIVE");
		assertThat(response.createdAt()).isNotNull();
		assertThat(walletRepository.findByUserId(userId)).isPresent();
	}

	@Test
	void returnsExistingWalletWhenUserIsProvisionedAgain() {
		UUID userId = UUID.randomUUID();

		InternalWalletProvisionResponse first = internalWalletProvisionService.provision(
				"internal-test-token",
				new InternalWalletProvisionRequest(userId));
		InternalWalletProvisionResponse second = internalWalletProvisionService.provision(
				"internal-test-token",
				new InternalWalletProvisionRequest(userId));

		assertThat(second.walletId()).isEqualTo(first.walletId());
		assertThat(second.balance()).isZero();
		assertThat(walletRepository.findByUserId(userId)).isPresent();
	}

	@Test
	void rejectsInvalidInternalTokenBeforeCreatingWallet() {
		UUID userId = UUID.randomUUID();

		assertThatThrownBy(() -> internalWalletProvisionService.provision(
				"wrong-token",
				new InternalWalletProvisionRequest(userId)))
				.isInstanceOf(InternalAuthenticationException.class);

		assertThat(walletRepository.findByUserId(userId)).isEmpty();
	}
}
