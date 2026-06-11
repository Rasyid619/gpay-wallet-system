package com.gpay.auth_service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/* Configuration for Auth Service calls to Wallet Service. */
@Validated
@ConfigurationProperties(prefix = "auth.wallet")
public record AuthWalletProperties(
		@NotNull URI provisionUrl,
		@NotBlank String internalToken,
		@NotNull @Positive Long provisionTimeoutMs) {
}
