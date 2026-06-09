package com.gpay.wallet_service.service;

import com.gpay.wallet_service.dto.WalletMutationPageResponse;
import com.gpay.wallet_service.dto.WalletMutationResponse;
import com.gpay.wallet_service.entity.LedgerEntry;
import com.gpay.wallet_service.entity.Transfer;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Reads authenticated wallet mutation history. */
@Service
@RequiredArgsConstructor
public class WalletMutationService {

	private static final int MAX_PAGE_SIZE = 100;

	private final LedgerEntryRepository ledgerEntryRepository;

	/**
	 * Returns paginated wallet mutations for the authenticated user's wallet.
	 *
	 * @param userId   authenticated user identifier
	 * @param pageable requested page and size
	 * @return paginated mutation response ordered newest first
	 */
	@Transactional(readOnly = true)
	public WalletMutationPageResponse getMutations(UUID userId, Pageable pageable) {
		PageRequest pageRequest = PageRequest.of(
				Math.max(pageable.getPageNumber(), 0),
				normalizeSize(pageable.getPageSize()),
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		Page<LedgerEntry> entries = ledgerEntryRepository.findByWalletUserId(userId, pageRequest);

		return new WalletMutationPageResponse(
				entries.getNumber(),
				entries.getSize(),
				entries.getTotalElements(),
				entries.getTotalPages(),
				entries.map(this::toResponse).toList());
	}

	private int normalizeSize(int size) {
		if (size < 1) {
			return 20;
		}

		return Math.min(size, MAX_PAGE_SIZE);
	}

	private WalletMutationResponse toResponse(LedgerEntry entry) {
		return new WalletMutationResponse(
				entry.getId(),
				entry.getEntryType().name(),
				entry.getSource().name(),
				entry.getAmount(),
				entry.getBalanceAfter(),
				resolveRelatedTransactionId(entry),
				entry.getCreatedAt());
	}

	private UUID resolveRelatedTransactionId(LedgerEntry entry) {
		Transfer transfer = entry.getTransfer();
		if (transfer != null) {
			return transfer.getId();
		}

		return entry.getPaymentTransactionId();
	}
}
