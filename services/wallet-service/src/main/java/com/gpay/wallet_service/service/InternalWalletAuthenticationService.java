package com.gpay.wallet_service.service;

import com.gpay.wallet_service.exception.InternalAuthenticationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/* Validates internal service-to-service wallet tokens. */
@Service
public class InternalWalletAuthenticationService {

	@Value("${wallet.internal-token}")
	private String internalToken;

	/**
	 * Validates a caller-provided internal token using constant-time comparison.
	 *
	 * @param providedInternalToken token from X-Internal-Token
	 */
	public void validate(String providedInternalToken) {
		if (internalToken == null || internalToken.isBlank()) {
			throw new InternalAuthenticationException("Internal wallet token is not configured");
		}

		byte[] expected = internalToken.getBytes(StandardCharsets.UTF_8);
		byte[] provided = Objects.requireNonNullElse(providedInternalToken, "").getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, provided)) {
			throw new InternalAuthenticationException("Internal authentication is invalid");
		}
	}
}
