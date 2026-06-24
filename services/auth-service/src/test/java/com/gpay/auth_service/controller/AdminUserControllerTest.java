package com.gpay.auth_service.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.auth_service.config.SecurityConfig;
import com.gpay.auth_service.dto.UserLookupResponse;
import com.gpay.auth_service.exception.GlobalExceptionHandler;
import com.gpay.auth_service.exception.NotFoundException;
import com.gpay.auth_service.security.JwtAuthFilter;
import com.gpay.auth_service.security.JwtService;
import com.gpay.auth_service.service.UserLookupService;
import com.gpay.common.tracing.TraceIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/* MVC tests for admin user lookup access control and response shaping. */
@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class, TraceIdFilter.class})
@TestPropertySource(properties = {
	"jwt.secret=test-secret-minimum-32-characters-long",
	"jwt.access-token-expiration-minutes=15"
})
class AdminUserControllerTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserLookupService userLookupService;

	@Test
	void returnsUserForAdmin() throws Exception {
		UUID userId = UUID.randomUUID();
		when(userLookupService.getUserById(eq(userId)))
				.thenReturn(new UserLookupResponse(userId, "user@example.com", "USER", Instant.parse("2026-01-01T00:00:00Z")));

		mockMvc.perform(get("/admin/users/{id}", userId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.email").value("user@example.com"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.created_at").exists());
	}

	@Test
	void returnsForbiddenForNonAdmin() throws Exception {
		mockMvc.perform(get("/admin/users/{id}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "USER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"))
				.andExpect(jsonPath("$.trace_id").exists());

		verifyNoInteractions(userLookupService);
	}

	@Test
	void returnsUnauthorizedWhenTokenIsMissing() throws Exception {
		mockMvc.perform(get("/admin/users/{id}", UUID.randomUUID()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

		verifyNoInteractions(userLookupService);
	}

	@Test
	void returnsNotFoundWhenUserMissing() throws Exception {
		UUID userId = UUID.randomUUID();
		when(userLookupService.getUserById(eq(userId)))
				.thenThrow(new NotFoundException("User not found"));

		mockMvc.perform(get("/admin/users/{id}", userId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "ADMIN")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	private String createToken(UUID userId, String role) {
		SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(userId.toString())
				.claim("role", role)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(900)))
				.signWith(key)
				.compact();
	}
}
