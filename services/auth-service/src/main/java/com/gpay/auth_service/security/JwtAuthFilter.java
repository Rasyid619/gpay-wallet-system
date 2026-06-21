package com.gpay.auth_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Intercepts every request and authenticates it when a valid Bearer token is present.
 *
 * <p>On success the authenticated principal is set in the {@link SecurityContextHolder}
 * as the user's {@link UUID}. On failure or missing token the request continues
 * unauthenticated; Spring Security will reject it if the endpoint requires auth.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String DEFAULT_ROLE = "USER";

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String authHeader = request.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = authHeader.substring(BEARER_PREFIX.length());
		try {
			Claims claims = jwtService.parseToken(token);
			UUID userId = UUID.fromString(claims.getSubject());
			String authority = "ROLE_" + resolveRole(claims);
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					userId, null, List.of(new SimpleGrantedAuthority(authority)));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (JwtException | IllegalArgumentException ignored) {
			// Invalid token — leave SecurityContext unauthenticated; Spring Security rejects protected routes
		}

		filterChain.doFilter(request, response);
	}

	private String resolveRole(Claims claims) {
		String role = claims.get("role", String.class);
		if (!StringUtils.hasText(role)) {
			return DEFAULT_ROLE;
		}

		return role;
	}
}
