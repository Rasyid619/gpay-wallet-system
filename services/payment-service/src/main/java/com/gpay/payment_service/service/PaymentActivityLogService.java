package com.gpay.payment_service.service;

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

/* Stores user-facing payment lifecycle activity logs. */
@Service
@RequiredArgsConstructor
public class PaymentActivityLogService {

	private static final String ACTION_GATEWAY_WEBHOOK = "PAYMENT_GATEWAY_WEBHOOK";
	private static final String ACTION_TOP_UP = "PAYMENT_TOP_UP";
	private static final String SERVICE_NAME = "payment-service";

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
}
