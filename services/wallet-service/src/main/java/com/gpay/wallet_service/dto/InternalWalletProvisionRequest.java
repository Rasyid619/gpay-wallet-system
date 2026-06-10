package com.gpay.wallet_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Internal request to create a wallet for an auth-service user.
 *
 * @param userId auth-service user identifier that will own the wallet
 */
public record InternalWalletProvisionRequest(
		@NotNull @JsonProperty("user_id") UUID userId) {
}
