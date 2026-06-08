package com.gpay.auth_service.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/* Utility for producing deterministic one-way hashes of opaque tokens. */
public final class HashUtil {

	private HashUtil() {
	}

	/**
	 * Returns the SHA-256 hex digest of the given value.
	 *
	 * <p>Used for refresh token storage and lookup. BCrypt is not suitable here
	 * because it is non-deterministic and therefore cannot be used for indexed lookup.
	 * Refresh tokens are already high-entropy random values so the slow factor of
	 * BCrypt is not needed.
	 *
	 * @param value raw token string
	 * @return lower-case hex-encoded SHA-256 digest
	 */
	public static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
