package com.gpay.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/* Unit tests for {@link JwtService} token validation. */
class JwtServiceTest {

	private static final String SECRET = "jwt-service-test-secret-minimum-32-characters-long";

	private final JwtService jwtService = new JwtService(SECRET);

	private String signedToken(String secret, UUID userId, String role) {
		SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(userId.toString())
				.claim("role", role)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(900)))
				.signWith(key)
				.compact();
	}

	@Test
	void parsesClaimsFromValidToken() {
		UUID userId = UUID.randomUUID();

		Claims claims = jwtService.parseToken(signedToken(SECRET, userId, "ADMIN"));

		assertThat(claims.getSubject()).isEqualTo(userId.toString());
		assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
	}

	@Test
	void throwsWhenTokenSignedWithDifferentSecret() {
		String token = signedToken("a-different-secret-minimum-32-characters-long", UUID.randomUUID(), "USER");

		assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(JwtException.class);
	}
}
