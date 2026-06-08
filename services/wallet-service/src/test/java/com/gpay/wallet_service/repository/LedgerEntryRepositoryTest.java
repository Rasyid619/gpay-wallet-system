package com.gpay.wallet_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gpay.wallet_service.constant.LedgerEntrySource;
import com.gpay.wallet_service.constant.LedgerEntryType;
import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.entity.LedgerEntry;
import com.gpay.wallet_service.entity.Wallet;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Repository tests for wallet mutation ownership filtering and ordering.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LedgerEntryRepositoryTest {

	@Autowired
	private LedgerEntryRepository ledgerEntryRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Test
	void findsOnlyMutationsForAuthenticatedUsersWalletNewestFirst() {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		Wallet wallet = walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				userId,
				100000L,
				WalletStatus.ACTIVE,
				Instant.now(),
				Instant.now()));
		Wallet otherWallet = walletRepository.save(Wallet.create(
				UUID.randomUUID(),
				otherUserId,
				100000L,
				WalletStatus.ACTIVE,
				Instant.now(),
				Instant.now()));
		LedgerEntry olderEntry = ledgerEntryRepository.save(createTopUpEntry(
				wallet,
				25000L,
				125000L,
				Instant.parse("2026-06-09T01:00:00Z")));
		LedgerEntry newerEntry = ledgerEntryRepository.save(createTopUpEntry(
				wallet,
				50000L,
				175000L,
				Instant.parse("2026-06-09T02:00:00Z")));
		ledgerEntryRepository.save(createTopUpEntry(
				otherWallet,
				99999L,
				199999L,
				Instant.parse("2026-06-09T03:00:00Z")));

		Page<LedgerEntry> page = ledgerEntryRepository.findByWalletUserId(
				userId,
				PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getContent())
				.extracting(LedgerEntry::getId)
				.containsExactly(newerEntry.getId(), olderEntry.getId());
	}

	private LedgerEntry createTopUpEntry(
			Wallet wallet,
			Long amount,
			Long balanceAfter,
			Instant createdAt) {
		return LedgerEntry.create(
				UUID.randomUUID(),
				wallet,
				null,
				UUID.randomUUID(),
				LedgerEntryType.CREDIT,
				LedgerEntrySource.TOP_UP,
				amount,
				balanceAfter,
				"Top-up received",
				createdAt);
	}
}
