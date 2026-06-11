package com.gpay.auth_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Internal request to provision a wallet for a registered user.
 *
 * @param userId auth-service user identifier that owns the wallet
 */
public record WalletProvisionRequest(
		@JsonProperty("user_id") UUID userId) {
}
