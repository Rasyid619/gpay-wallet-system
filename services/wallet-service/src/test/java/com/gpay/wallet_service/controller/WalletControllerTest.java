package com.gpay.wallet_service.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.wallet_service.config.SecurityConfig;
import com.gpay.wallet_service.dto.WalletBalanceResponse;
import com.gpay.wallet_service.exception.GlobalExceptionHandler;
import com.gpay.wallet_service.exception.WalletNotFoundException;
import com.gpay.wallet_service.security.JwtAuthFilter;
import com.gpay.wallet_service.security.JwtService;
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

/**
 * MVC tests for authenticated wallet balance access.
 */
@WebMvcTest(WalletController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=test-secret-minimum-32-characters-long")
class WalletControllerTest {

	private static final String JWT_SECRET = "test-secret-minimum-32-characters-long";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WalletBalanceService walletBalanceService;

	@Test
	void returnsBalanceForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		when(walletBalanceService.getBalance(userId)).thenReturn(new WalletBalanceResponse(walletId, 125000L));

		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.balance").value(125000));
	}

	@Test
	void returnsUnauthorizedWhenTokenIsMissing() throws Exception {
		mockMvc.perform(get("/wallets/balance"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(walletBalanceService);
	}

	@Test
	void returnsUnauthorizedWhenTokenIsInvalid() throws Exception {
		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here"))
				.andExpect(status().isUnauthorized());

		verifyNoInteractions(walletBalanceService);
	}

	@Test
	void returnsNotFoundWhenAuthenticatedUserHasNoWallet() throws Exception {
		UUID userId = UUID.randomUUID();
		when(walletBalanceService.getBalance(userId))
				.thenThrow(new WalletNotFoundException("Wallet was not found for authenticated user"));

		mockMvc.perform(get("/wallets/balance")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(userId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("Wallet was not found for authenticated user"));
	}

	private String createToken(UUID userId) {
		Instant now = Instant.now();
		SecretKey signingKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
		return Jwts.builder()
				.subject(userId.toString())
				.claim("email", "user@example.com")
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(900)))
				.signWith(signingKey)
				.compact();
	}
}
