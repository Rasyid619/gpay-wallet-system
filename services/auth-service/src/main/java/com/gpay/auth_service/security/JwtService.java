package com.gpay.auth_service.security;

import com.gpay.auth_service.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/* Generates and validates JWT access tokens. */
@Service
public class JwtService {

	private final SecretKey signingKey;
	private final long accessTokenExpirationMs;

	public JwtService(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration-minutes}") int accessTokenExpirationMinutes) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpirationMs = (long) accessTokenExpirationMinutes * 60 * 1000;
	}

	/**
	 * Generates a signed JWT access token for the given user.
	 *
	 * @param userId user identifier embedded as the token subject
	 * @param email  user email embedded as a claim
	 * @param role   user role embedded as the {@code role} claim for downstream RBAC
	 * @return compact signed JWT string
	 */
	public String generateAccessToken(UUID userId, String email, UserRole role) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + accessTokenExpirationMs);
		return Jwts.builder()
				.subject(userId.toString())
				.claim("email", email)
				.claim("role", role.name())
				.issuedAt(now)
				.expiration(expiry)
				.signWith(signingKey)
				.compact();
	}

	/**
	 * Parses and validates a JWT token, returning its claims.
	 *
	 * @param token compact JWT string
	 * @return verified claims payload
	 * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
	 */
	public Claims parseToken(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
