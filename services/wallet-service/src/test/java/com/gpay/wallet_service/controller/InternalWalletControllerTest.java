package com.gpay.wallet_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.wallet_service.config.SecurityConfig;
import com.gpay.common.tracing.TraceIdFilter;
import com.gpay.wallet_service.dto.InternalWalletProvisionRequest;
import com.gpay.wallet_service.dto.InternalWalletProvisionResponse;
import com.gpay.wallet_service.exception.GlobalExceptionHandler;
import com.gpay.wallet_service.security.JwtAuthFilter;
import com.gpay.wallet_service.security.JwtService;
import com.gpay.wallet_service.service.InternalWalletProvisionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MVC tests for internal wallet provision access.
 */
@WebMvcTest(InternalWalletController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class, GlobalExceptionHandler.class, TraceIdFilter.class})
@TestPropertySource(properties = "jwt.secret=test-secret-minimum-32-characters-long")
class InternalWalletControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private InternalWalletProvisionService internalWalletProvisionService;

	@Test
	void returnsProvisionResponseForInternalRequest() throws Exception {
		UUID walletId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-06-09T04:00:00Z");
		when(internalWalletProvisionService.provision(
				eq("internal-token"),
				any(InternalWalletProvisionRequest.class)))
				.thenReturn(new InternalWalletProvisionResponse(
						walletId,
						userId,
						0L,
						"ACTIVE",
						createdAt));

		mockMvc.perform(post("/internal/wallets/provision")
						.header("X-Internal-Token", "internal-token")
						.header("X-Trace-Id", "trace-provision")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "user_id": "%s"
								}
								""".formatted(userId)))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Trace-Id", "trace-provision"))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.user_id").value(userId.toString()))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.created_at").value("2026-06-09T04:00:00Z"));
	}
}
