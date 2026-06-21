package com.gpay.payment_service.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Integration tests for the admin payment lookup endpoint, covering admin access to any owner's
 * payment, the non-admin authorization guard, and the unknown-id branch against the real schema.
 */
class AdminPaymentLookupIntegrationTest extends AbstractIntegrationTest {

	private final MockMvc mockMvc;
	private final TopupTransactionRepository topupTransactionRepository;
	private final JdbcTemplate jdbcTemplate;

	@Autowired
	AdminPaymentLookupIntegrationTest(
			MockMvc mockMvc,
			TopupTransactionRepository topupTransactionRepository,
			JdbcTemplate jdbcTemplate) {
		this.mockMvc = mockMvc;
		this.topupTransactionRepository = topupTransactionRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	private TopupTransaction seedPayment(UUID ownerId, UUID walletId) {
		TopupTransaction transaction = TopupTransaction.createPending(
				UUID.randomUUID(),
				ownerId,
				walletId,
				75_000L,
				"admin-lookup-key",
				"trace-admin-seed",
				Instant.now());
		return topupTransactionRepository.save(transaction);
	}

	@Test
	void returnsAnyOwnersPaymentForAdmin() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		TopupTransaction seeded = seedPayment(ownerId, walletId);
		UUID adminId = UUID.randomUUID();

		mockMvc.perform(get("/admin/payments/{id}", seeded.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(adminId, "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payment_transaction_id").value(seeded.getId().toString()))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.amount").value(75000))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.created_at").exists());
	}

	@Test
	void writesAuditLogOnAdminLookup() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID walletId = UUID.randomUUID();
		TopupTransaction seeded = seedPayment(ownerId, walletId);
		UUID adminId = UUID.randomUUID();

		mockMvc.perform(get("/admin/payments/{id}", seeded.getId())
						.header("X-Trace-Id", "trace-admin-lookup")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(adminId, "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payment_transaction_id").value(seeded.getId().toString()))
				.andExpect(jsonPath("$.wallet_id").value(walletId.toString()))
				.andExpect(jsonPath("$.amount").value(75000))
				.andExpect(jsonPath("$.status").value("PENDING"));

		Long auditCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM activity_logs WHERE action = 'ADMIN_PAYMENT_LOOKUP'",
				Long.class);
		assertThat(auditCount).isEqualTo(1L);

		Map<String, Object> audit = jdbcTemplate.queryForMap(
				"SELECT user_id, transaction_id, trace_id, request_payload::text AS request_payload "
						+ "FROM activity_logs WHERE action = 'ADMIN_PAYMENT_LOOKUP'");
		assertThat(audit.get("user_id")).hasToString(adminId.toString());
		assertThat(audit.get("transaction_id")).hasToString(seeded.getId().toString());
		assertThat(audit.get("trace_id")).isEqualTo("trace-admin-lookup");
		assertThat(audit.get("request_payload").toString())
				.contains("target_user_id")
				.contains(ownerId.toString())
				.contains("wallet_id")
				.contains(walletId.toString());
	}

	@Test
	void rejectsLookupForNonAdmin() throws Exception {
		TopupTransaction seeded = seedPayment(UUID.randomUUID(), UUID.randomUUID());

		mockMvc.perform(get("/admin/payments/{id}", seeded.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "USER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"));
	}

	@Test
	void returnsNotFoundForUnknownPayment() throws Exception {
		mockMvc.perform(get("/admin/payments/{id}", UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + createToken(UUID.randomUUID(), "ADMIN")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}
}
