package com.gpay.auth_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link JwtAuthFilter} covering the bearer-prefix guard, valid token
 * authentication, role-claim authority resolution, and silent handling of an invalid token.
 */
class JwtAuthFilterTest {

	private static final String SECRET = "jwt-filter-test-secret-minimum-32-characters-long";

	private final JwtService jwtService = new JwtService(SECRET, 15);
	private final JwtAuthFilter filter = new JwtAuthFilter(jwtService);

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private String validToken(UUID userId) {
		return tokenWithRole(userId, null);
	}

	private String tokenWithRole(UUID userId, String role) {
		SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
		Instant now = Instant.now();
		var builder = Jwts.builder()
				.subject(userId.toString())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(900)));
		if (role != null) {
			builder.claim("role", role);
		}

		return builder.signWith(key).compact();
	}

	@Test
	void leavesContextUnauthenticatedWhenHeaderIsNotBearer() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(chain).doFilter(request, response);
	}

	@Test
	void authenticatesUserForValidBearerToken() throws Exception {
		UUID userId = UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + validToken(userId));
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
		verify(chain).doFilter(request, response);
	}

	@Test
	void grantsAdminAuthorityWhenRoleClaimIsAdmin() throws Exception {
		UUID userId = UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + tokenWithRole(userId, "ADMIN"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_ADMIN");
		verify(chain).doFilter(request, response);
	}

	@Test
	void grantsUserAuthorityWhenRoleClaimIsUser() throws Exception {
		UUID userId = UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + tokenWithRole(userId, "USER"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_USER");
		verify(chain).doFilter(request, response);
	}

	@Test
	void defaultsToUserAuthorityWhenRoleClaimIsAbsent() throws Exception {
		UUID userId = UUID.randomUUID();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + validToken(userId));
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
				.extracting("authority")
				.containsExactly("ROLE_USER");
		verify(chain).doFilter(request, response);
	}

	@Test
	void leavesContextUnauthenticatedForInvalidBearerToken() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer invalid.token.value");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		verify(chain).doFilter(request, response);
	}
}
