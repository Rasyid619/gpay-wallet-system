package com.gpay.auth_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gpay.auth_service.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/* Unit tests for {@link JwtService} access-token claim contents. */
class JwtServiceTest {

	private static final String SECRET = "jwt-service-test-secret-minimum-32-characters-long";

	private final JwtService jwtService = new JwtService(SECRET, 15);

	@Test
	void embedsRoleClaimInAccessToken() {
		UUID userId = UUID.randomUUID();

		String token = jwtService.generateAccessToken(userId, "admin@example.com", UserRole.ADMIN);

		Claims claims = Jwts.parser()
				.verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
				.build()
				.parseSignedClaims(token)
				.getPayload();
		assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
		assertThat(claims.getSubject()).isEqualTo(userId.toString());
		assertThat(claims.get("email", String.class)).isEqualTo("admin@example.com");
	}
}
