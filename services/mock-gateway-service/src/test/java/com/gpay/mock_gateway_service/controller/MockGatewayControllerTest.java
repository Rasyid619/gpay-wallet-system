package com.gpay.mock_gateway_service.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gpay.mock_gateway_service.exception.GlobalExceptionHandler;
import com.gpay.mock_gateway_service.service.MockGatewayTopUpService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MockGatewayController.class)
@Import({MockGatewayTopUpService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "mock-gateway.timeout-delay-ms=10")
class MockGatewayControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void acceptsSuccessTopUpSimulation() throws Exception {
		UUID paymentTransactionId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();

		mockMvc.perform(post("/mock-gateway/top-up")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(paymentTransactionId, walletId, "SUCCESS")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payment_transaction_id").value(paymentTransactionId.toString()))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.amount").value(75000))
				.andExpect(jsonPath("$.mode").value("SUCCESS"))
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.gateway_reference", startsWith("gw-")));
	}

	@Test
	void acceptsFailedTopUpSimulation() throws Exception {
		mockMvc.perform(post("/mock-gateway/top-up")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(UUID.randomUUID(), UUID.randomUUID(), "FAILED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("FAILED"))
				.andExpect(jsonPath("$.status").value("FAILED"));
	}

	@Test
	void acceptsTimeoutTopUpSimulation() throws Exception {
		mockMvc.perform(post("/mock-gateway/top-up")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(UUID.randomUUID(), UUID.randomUUID(), "TIMEOUT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("TIMEOUT"))
				.andExpect(jsonPath("$.status").value("TIMEOUT"));
	}

	@Test
	void returnsBadRequestWhenModeIsInvalid() throws Exception {
		mockMvc.perform(post("/mock-gateway/top-up")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest(UUID.randomUUID(), UUID.randomUUID(), "UNKNOWN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}

	@Test
	void returnsBadRequestWhenAmountIsInvalid() throws Exception {
		mockMvc.perform(post("/mock-gateway/top-up")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "payment_transaction_id": "%s",
								  "wallet_id": "%s",
								  "amount": 0,
								  "mode": "SUCCESS"
								}
								""".formatted(UUID.randomUUID(), UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
	}

	private String validRequest(UUID paymentTransactionId, UUID walletId, String mode) {
		return """
				{
				  "payment_transaction_id": "%s",
				  "wallet_id": "%s",
				  "amount": 75000,
				  "mode": "%s"
				}
				""".formatted(paymentTransactionId, walletId, mode);
	}
}
