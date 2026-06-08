package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated wallet mutation response.
 *
 * @param page       zero-based page number
 * @param size       requested page size
 * @param totalItems total mutation rows matching the authenticated wallet
 * @param totalPages total available pages
 * @param items      wallet mutation rows ordered newest first
 */
public record WalletMutationPageResponse(
		Integer page,
		Integer size,
		@JsonProperty("total_items") Long totalItems,
		@JsonProperty("total_pages") Integer totalPages,
		List<WalletMutationResponse> items) {
}
