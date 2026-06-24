package com.gpay.wallet_service.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.common.security.SecurityAutoConfiguration;
import com.gpay.common.tracing.TraceIdFilter;
import com.gpay.wallet_service.config.SecurityConfig;
import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.exception.GlobalExceptionHandler;
import com.gpay.wallet_service.exception.WalletNotFoundException;
import com.gpay.wallet_service.service.WalletBalanceService;
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

/* MVC tests for admin wallet lookup access control and response shaping. */
@WebMvcTest(AdminWalletController.class)
@Import({SecurityConfig.class, SecurityAutoConfiguration.class, GlobalExceptionHandler.class, TraceIdFilter.class})
@TestPropertySource(properties = "jwt.secret=test-secret-minimum-32-characters-long")
class AdminWalletControllerTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WalletBalanceService walletBalanceService;

	@Test
	void returnsWalletForAdmin() throws Exception {
		UUID walletId = UUID.randomUUID();
		when(walletBalanceService.getWalletById(eq(walletId)))
				.thenReturn(new WalletBalanceResponse(walletId, 175_000L));

		mockMvc.perform(get("/admin/wallets/{id}", walletId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.balance").value(175000));
	}

	@Test
	void returnsForbiddenForNonAdmin() throws Exception {
		mockMvc.perform(get("/admin/wallets/{id}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "USER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"))
				.andExpect(jsonPath("$.trace_id").exists());

		verifyNoInteractions(walletBalanceService);
	}

	@Test
	void returnsUnauthorizedWhenTokenIsMissing() throws Exception {
		mockMvc.perform(get("/admin/wallets/{id}", UUID.randomUUID()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

		verifyNoInteractions(walletBalanceService);
	}

	@Test
	void returnsNotFoundWhenWalletMissing() throws Exception {
		UUID walletId = UUID.randomUUID();
		when(walletBalanceService.getWalletById(eq(walletId)))
				.thenThrow(new WalletNotFoundException("Wallet was not found"));

		mockMvc.perform(get("/admin/wallets/{id}", walletId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "ADMIN")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"));
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
