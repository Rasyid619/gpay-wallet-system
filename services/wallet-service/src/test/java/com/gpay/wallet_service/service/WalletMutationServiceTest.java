package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.gpay.wallet_service.constant.LedgerEntrySource;
import com.gpay.wallet_service.constant.LedgerEntryType;
import com.gpay.wallet_service.constant.TransferStatus;
import com.gpay.wallet_service.constant.WalletStatus;
import com.gpay.wallet_service.dto.WalletMutationPageResponse;
import com.gpay.wallet_service.dto.WalletMutationResponse;
import com.gpay.wallet_service.entity.LedgerEntry;
import com.gpay.wallet_service.entity.Transfer;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for authenticated wallet mutation history lookup.
 */
@ExtendWith(MockitoExtension.class)
class WalletMutationServiceTest {

	@Mock
	private LedgerEntryRepository ledgerEntryRepository;

	private WalletMutationService walletMutationService;

	@BeforeEach
	void setUp() {
		walletMutationService = new WalletMutationService(ledgerEntryRepository);
	}

	@Nested
	class GetMutations {

		@Test
		void returnsPaginatedMutationResponses() {
			UUID userId = UUID.randomUUID();
			UUID walletId = UUID.randomUUID();
			UUID transferId = UUID.randomUUID();
			Wallet wallet = Wallet.create(walletId, userId, 100000L, WalletStatus.ACTIVE, Instant.now(), Instant.now());
			Transfer transfer = Transfer.create(
					transferId,
					wallet,
					Wallet.create(UUID.randomUUID(), UUID.randomUUID(), 0L, WalletStatus.ACTIVE, Instant.now(), Instant.now()),
					25000L,
					TransferStatus.SUCCESS,
					null,
					Instant.parse("2026-06-09T01:00:00Z"));
			LedgerEntry entry = LedgerEntry.create(
					UUID.randomUUID(),
					wallet,
					transfer,
					null,
					LedgerEntryType.DEBIT,
					LedgerEntrySource.TRANSFER,
					25000L,
					75000L,
					"Transfer sent",
					Instant.parse("2026-06-09T02:00:00Z"));
			PageRequest requestedPage = PageRequest.of(1, 2);
			ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
			when(ledgerEntryRepository.findByWalletUserId(
					org.mockito.ArgumentMatchers.eq(userId),
					pageableCaptor.capture()))
					.thenReturn(new PageImpl<>(List.of(entry), requestedPage, 3));

			WalletMutationPageResponse response = walletMutationService.getMutations(userId, requestedPage);

			assertThat(response.page()).isEqualTo(1);
			assertThat(response.size()).isEqualTo(2);
			assertThat(response.totalItems()).isEqualTo(3L);
			assertThat(response.totalPages()).isEqualTo(2);
			assertThat(response.items()).hasSize(1);

			WalletMutationResponse mutation = response.items().getFirst();
			assertThat(mutation.mutationId()).isEqualTo(entry.getId());
			assertThat(mutation.type()).isEqualTo("DEBIT");
			assertThat(mutation.source()).isEqualTo("TRANSFER");
			assertThat(mutation.amount()).isEqualTo(25000L);
			assertThat(mutation.balanceAfter()).isEqualTo(75000L);
			assertThat(mutation.relatedTransactionId()).isEqualTo(transferId);
			assertThat(mutation.createdAt()).isEqualTo(Instant.parse("2026-06-09T02:00:00Z"));

			Pageable pageable = pageableCaptor.getValue();
			assertThat(pageable.getPageNumber()).isEqualTo(1);
			assertThat(pageable.getPageSize()).isEqualTo(2);
			assertThat(pageable.getSort().getOrderFor("createdAt")).isEqualTo(Sort.Order.desc("createdAt"));
			assertThat(pageable.getSort().getOrderFor("id")).isEqualTo(Sort.Order.desc("id"));
		}

		@Test
		void capsPageSizeToOneHundred() {
			UUID userId = UUID.randomUUID();
			ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
			when(ledgerEntryRepository.findByWalletUserId(
					org.mockito.ArgumentMatchers.eq(userId),
					pageableCaptor.capture()))
					.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

			walletMutationService.getMutations(userId, PageRequest.of(0, 500));

			assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
		}
	}
}
