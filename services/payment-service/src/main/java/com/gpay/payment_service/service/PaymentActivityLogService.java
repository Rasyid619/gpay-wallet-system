package com.gpay.payment_service.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.dto.GatewayWebhookRequest;
import com.gpay.payment_service.dto.GatewayWebhookResponse;
import com.gpay.payment_service.dto.TopUpRequest;
import com.gpay.payment_service.dto.TopUpResponse;
import com.gpay.payment_service.entity.ActivityLog;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.repository.ActivityLogRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/* Stores user-facing payment lifecycle activity logs. */
@Service
@RequiredArgsConstructor
public class PaymentActivityLogService {

	private static final String ACTION_ADMIN_PAYMENT_LOOKUP = "ADMIN_PAYMENT_LOOKUP";
	private static final String ACTION_GATEWAY_WEBHOOK = "PAYMENT_GATEWAY_WEBHOOK";
	private static final String ACTION_TOP_UP = "PAYMENT_TOP_UP";
	private static final String SERVICE_NAME = "payment-service";
	private static final String STATUS_NOT_FOUND = "NOT_FOUND";
	private static final String STATUS_SUCCESS = "SUCCESS";

	private final ActivityLogRepository activityLogRepository;
	private final ObjectMapper objectMapper;

	/**
	 * Logs an accepted payment top-up request.
	 *
	 * @param transaction persisted top-up transaction
	 * @param request     top-up request payload
	 * @param response    API response payload
	 * @param durationMs  processing duration in milliseconds
	 * @param now         activity creation timestamp
	 */
	public void logTopUpCreated(
			TopupTransaction transaction,
			TopUpRequest request,
			TopUpResponse response,
			Long durationMs,
			Instant now) {
		saveActivityLog(
				transaction,
				ACTION_TOP_UP,
				transaction.getStatus().name(),
				request,
				response,
				durationMs,
				now);
	}

	/**
	 * Logs a gateway webhook status transition.
	 *
	 * @param transaction updated top-up transaction
	 * @param request     gateway webhook request payload
	 * @param response    gateway webhook response payload
	 * @param durationMs  processing duration in milliseconds
	 * @param now         activity creation timestamp
	 */
	public void logGatewayWebhookProcessed(
			TopupTransaction transaction,
			GatewayWebhookRequest request,
			GatewayWebhookResponse response,
			Long durationMs,
			Instant now) {
		saveActivityLog(
				transaction,
				ACTION_GATEWAY_WEBHOOK,
				transaction.getStatus().name(),
				request,
				response,
				durationMs,
				now);
	}

	/**
	 * Audits a privileged admin cross-owner payment lookup.
	 *
	 * <p>This is the reusable pattern for auditing privileged admin or support reads of records they do
	 * not own: the audit row records the acting principal as {@code user_id} and the current request
	 * {@code trace_id}, never the looked-up record's owner or its originating trace. Future admin and
	 * support read endpoints should follow this shape rather than the owner-derived
	 * {@code saveActivityLog} helper. The cross-owner target is captured in the request payload JSON.
	 *
	 * @param adminUserId acting admin principal performing the lookup
	 * @param transaction the looked-up payment transaction owned by another user
	 * @param traceId     current request trace identifier
	 * @param now         audit creation timestamp
	 */
	public void logAdminPaymentLookup(
			UUID adminUserId,
			TopupTransaction transaction,
			String traceId,
			Instant now) {
		AdminPaymentLookupAudit audit = new AdminPaymentLookupAudit(
				transaction.getUserId(),
				transaction.getWalletId());
		activityLogRepository.save(ActivityLog.create(
				UUID.randomUUID(),
				traceId,
				adminUserId,
				transaction.getId(),
				SERVICE_NAME,
				ACTION_ADMIN_PAYMENT_LOOKUP,
				STATUS_SUCCESS,
				writeJson(audit),
				null,
				null,
				now));
	}

	/**
	 * Audits an admin/support payment lookup that resolved to no transaction.
	 *
	 * <p>Runs in its own transaction so the audit row survives the {@code NotFoundException} rollback of
	 * the calling lookup. The probed id is stored in the payload because no transaction row exists to
	 * reference. Follow this pattern for unknown-id branches of future admin/support read endpoints.
	 *
	 * @param adminUserId       acting admin principal performing the lookup
	 * @param requestedPaymentId payment transaction id that was probed
	 * @param traceId           current request trace identifier
	 * @param now               activity creation timestamp
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void logAdminPaymentLookupNotFound(
			UUID adminUserId,
			UUID requestedPaymentId,
			String traceId,
			Instant now) {
		AdminPaymentLookupNotFoundAudit audit = new AdminPaymentLookupNotFoundAudit(requestedPaymentId);
		activityLogRepository.save(ActivityLog.create(
				UUID.randomUUID(),
				traceId,
				adminUserId,
				null,
				SERVICE_NAME,
				ACTION_ADMIN_PAYMENT_LOOKUP,
				STATUS_NOT_FOUND,
				writeJson(audit),
				null,
				null,
				now));
	}

	private void saveActivityLog(
			TopupTransaction transaction,
			String action,
			String status,
			Object requestPayload,
			Object responsePayload,
			Long durationMs,
			Instant now) {
		activityLogRepository.save(ActivityLog.create(
				UUID.randomUUID(),
				transaction.getTraceId(),
				transaction.getUserId(),
				transaction.getId(),
				SERVICE_NAME,
				action,
				status,
				writeJson(requestPayload),
				writeJson(responsePayload),
				durationMs,
				now));
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Payment activity payload could not be serialized", ex);
		}
	}

	/**
	 * Cross-owner target captured in an admin payment lookup audit payload.
	 *
	 * @param targetUserId owner of the looked-up payment transaction
	 * @param walletId     wallet of the looked-up payment transaction
	 */
	private record AdminPaymentLookupAudit(
			@JsonProperty("target_user_id") UUID targetUserId,
			@JsonProperty("wallet_id") UUID walletId) {
	}

	/**
	 * Probed payment id captured in an admin payment lookup audit when no transaction was found.
	 *
	 * @param requestedPaymentId payment transaction id that the admin attempted to read
	 */
	private record AdminPaymentLookupNotFoundAudit(
			@JsonProperty("requested_payment_id") UUID requestedPaymentId) {
	}
}
