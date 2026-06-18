package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.dto.IdempotentResponse;
import com.gpay.wallet_service.dto.InternalWalletCreditRequest;
import com.gpay.wallet_service.dto.InternalWalletCreditResponse;
import com.gpay.wallet_service.entity.LedgerEntry;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.exception.BadRequestException;
import com.gpay.wallet_service.exception.IdempotencyConflictException;
import com.gpay.wallet_service.exception.InternalAuthenticationException;
import com.gpay.wallet_service.exception.PaymentTransactionConflictException;
import com.gpay.wallet_service.repository.ActivityLogRepository;
import com.gpay.wallet_service.repository.IdempotencyKeyRepository;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for internal wallet credit behavior.
 */
@SpringBootTest(properties = "wallet.internal-token=internal-test-token")
class InternalWalletCreditServiceTest {

	@Autowired
	private ActivityLogRepository activityLogRepository;

	@Autowired
	private IdempotencyKeyRepository idempotencyKeyRepository;

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private InternalWalletCreditService internalWalletCreditService;

	@Autowired
	private WalletRepository walletRepository;

	@Test
	void creditsWalletAndCreatesOneCreditLedgerEntry() {
		Wallet wallet = saveWallet(100000L);
		UUID paymentTransactionId = UUID.randomUUID();

		IdempotentResponse result = internalWalletCreditService.credit(
				"internal-test-token",
				"credit-success-" + UUID.randomUUID(),
				new InternalWalletCreditRequest(wallet.getId(), paymentTransactionId, 50000L),
				"trace-credit");

		assertThat(result.status()).isEqualTo(200);
		assertThat(result.body()).isInstanceOf(InternalWalletCreditResponse.class);
		InternalWalletCreditResponse body = (InternalWalletCreditResponse) result.body();
		assertThat(body.walletId()).isEqualTo(wallet.getId());
		assertThat(body.paymentTransactionId()).isEqualTo(paymentTransactionId);
		assertThat(body.amount()).isEqualTo(50000L);
		assertThat(body.balanceAfter()).isEqualTo(150000L);
		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(150000L);

		LedgerEntry ledgerEntry = ledgerEntryRepository.findByPaymentTransactionId(paymentTransactionId).orElseThrow();
		assertThat(ledgerEntry.getWallet().getId()).isEqualTo(wallet.getId());
		assertThat(ledgerEntry.getAmount()).isEqualTo(50000L);
		assertThat(ledgerEntry.getBalanceAfter()).isEqualTo(150000L);
		assertThat(activityLogRepository.countByWalletId(wallet.getId())).isEqualTo(1);
	}

	@Test
	void replaysSameIdempotencyKeyAndPayloadWithoutDoubleCredit() {
		Wallet wallet = saveWallet(100000L);
		UUID paymentTransactionId = UUID.randomUUID();
		String idempotencyKey = "credit-replay-" + UUID.randomUUID();
		InternalWalletCreditRequest request = new InternalWalletCreditRequest(wallet.getId(), paymentTransactionId, 50000L);

		IdempotentResponse first = internalWalletCreditService.credit(
				"internal-test-token",
				idempotencyKey,
				request,
				"trace-first");
		IdempotentResponse second = internalWalletCreditService.credit(
				"internal-test-token",
				idempotencyKey,
				request,
				"trace-second");

		assertThat(first.status()).isEqualTo(200);
		assertThat(second.status()).isEqualTo(200);
		assertThat(second.body()).isInstanceOf(JsonNode.class);
		assertThat(((JsonNode) second.body()).get("balance_after").asLong()).isEqualTo(150000L);
		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(150000L);
		assertThat(activityLogRepository.countByWalletId(wallet.getId())).isEqualTo(1);
	}

	@Test
	void replaysSamePaymentTransactionWithNewIdempotencyKeyWithoutDoubleCredit() {
		Wallet wallet = saveWallet(100000L);
		UUID paymentTransactionId = UUID.randomUUID();
		InternalWalletCreditRequest request = new InternalWalletCreditRequest(wallet.getId(), paymentTransactionId, 50000L);

		internalWalletCreditService.credit(
				"internal-test-token",
				"credit-payment-first-" + UUID.randomUUID(),
				request,
				"trace-first");
		IdempotentResponse second = internalWalletCreditService.credit(
				"internal-test-token",
				"credit-payment-second-" + UUID.randomUUID(),
				request,
				"trace-second");

		assertThat(second.status()).isEqualTo(200);
		assertThat(second.body()).isInstanceOf(InternalWalletCreditResponse.class);
		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(150000L);
		assertThat(activityLogRepository.countByWalletId(wallet.getId())).isEqualTo(1);
	}

	@Test
	void rejectsSameIdempotencyKeyWithDifferentPayload() {
		Wallet wallet = saveWallet(100000L);
		String idempotencyKey = "credit-conflict-" + UUID.randomUUID();

		internalWalletCreditService.credit(
				"internal-test-token",
				idempotencyKey,
				new InternalWalletCreditRequest(wallet.getId(), UUID.randomUUID(), 50000L),
				"trace-conflict");

		assertThatThrownBy(() -> internalWalletCreditService.credit(
				"internal-test-token",
				idempotencyKey,
				new InternalWalletCreditRequest(wallet.getId(), UUID.randomUUID(), 50000L),
				"trace-conflict"))
				.isInstanceOf(IdempotencyConflictException.class);
	}

	@Test
	void rejectsSamePaymentTransactionWithDifferentPayload() {
		Wallet wallet = saveWallet(100000L);
		UUID paymentTransactionId = UUID.randomUUID();

		internalWalletCreditService.credit(
				"internal-test-token",
				"credit-payment-conflict-first-" + UUID.randomUUID(),
				new InternalWalletCreditRequest(wallet.getId(), paymentTransactionId, 50000L),
				"trace-conflict");

		assertThatThrownBy(() -> internalWalletCreditService.credit(
				"internal-test-token",
				"credit-payment-conflict-second-" + UUID.randomUUID(),
				new InternalWalletCreditRequest(wallet.getId(), paymentTransactionId, 60000L),
				"trace-conflict"))
				.isInstanceOf(PaymentTransactionConflictException.class);
		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(150000L);
	}

	@Test
	void rejectsCreditWhenIdempotencyKeyIsNull() {
		Wallet wallet = saveWallet(100000L);

		assertThatThrownBy(() -> internalWalletCreditService.credit(
				"internal-test-token",
				null,
				new InternalWalletCreditRequest(wallet.getId(), UUID.randomUUID(), 50000L),
				"trace-null-key"))
				.isInstanceOf(BadRequestException.class);

		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(100000L);
	}

	@Test
	void rejectsInvalidInternalTokenBeforeBalanceChange() {
		Wallet wallet = saveWallet(100000L);

		assertThatThrownBy(() -> internalWalletCreditService.credit(
				"wrong-token",
				"credit-invalid-auth-" + UUID.randomUUID(),
				new InternalWalletCreditRequest(wallet.getId(), UUID.randomUUID(), 50000L),
				"trace-auth"))
				.isInstanceOf(InternalAuthenticationException.class);

		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(100000L);
		assertThat(activityLogRepository.countByWalletId(wallet.getId())).isZero();
	}

	@Test
	void rollsBackBalanceLedgerIdempotencyAndActivityLogWhenCreditFails() {
		Wallet wallet = saveWallet(1L);
		UUID paymentTransactionId = UUID.randomUUID();
		String idempotencyKey = "credit-rollback-" + UUID.randomUUID();

		assertThatThrownBy(() -> internalWalletCreditService.credit(
				"internal-test-token",
				idempotencyKey,
				new InternalWalletCreditRequest(wallet.getId(), paymentTransactionId, Long.MAX_VALUE),
				"trace-rollback"))
				.isInstanceOf(RuntimeException.class);

		assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance()).isEqualTo(1L);
		assertThat(ledgerEntryRepository.findByPaymentTransactionId(paymentTransactionId)).isEmpty();
		assertThat(idempotencyKeyRepository.existsByIdempotencyKey(idempotencyKey)).isFalse();
		assertThat(activityLogRepository.countByWalletId(wallet.getId())).isZero();
	}

	private Wallet saveWallet(Long balance) {
		Instant now = Instant.now();
		return walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				UUID.randomUUID(),
				balance,
				WalletStatus.ACTIVE,
				now,
				now));
	}
}
