package com.gpay.payment_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/* Validates JWT access tokens issued by auth-service. */
@Service
public class JwtService {

	private final SecretKey signingKey;

	public JwtService(@Value("${jwt.secret}") String secret) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
