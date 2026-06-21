package com.gpay.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.dto.IdempotentResponse;
import com.gpay.payment_service.dto.TopUpRequest;
import com.gpay.payment_service.dto.TopUpResponse;
import com.gpay.payment_service.entity.IdempotencyKey;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.exception.BadRequestException;
import com.gpay.payment_service.exception.IdempotencyConflictException;
import com.gpay.payment_service.exception.NotFoundException;
import com.gpay.payment_service.repository.IdempotencyKeyRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/* Creates authenticated payment top-up transactions with durable idempotency. */
@Service
@RequiredArgsConstructor
public class PaymentTopUpService {

	private final IdempotencyKeyRepository idempotencyKeyRepository;
	private final ObjectMapper objectMapper;
	private final PaymentActivityLogService paymentActivityLogService;
	private final PaymentGatewayClient paymentGatewayClient;
	private final PaymentRateLimiter paymentRateLimiter;
	private final TopupTransactionRepository topupTransactionRepository;

	/**
	 * Creates or replays an idempotent top-up response.
	 *
	 * @param userId         authenticated user identifier
	 * @param idempotencyKey client-provided durable idempotency key
	 * @param request        top-up request payload
	 * @param traceId        request trace identifier when supplied
	 * @return status and body for the top-up response
	 */
	@Transactional
	public IdempotentResponse topUp(
			UUID userId,
			String idempotencyKey,
			TopUpRequest request,
			String traceId) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BadRequestException("Idempotency-Key header is required");
		}

		paymentRateLimiter.checkTopUpAllowed(userId);

		String requestHash = hashRequest(request);
		Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(
				userId,
				idempotencyKey);
		if (existing.isPresent()) {
			return replayOrConflict(existing.get(), requestHash);
		}

		return processTopUp(userId, idempotencyKey, requestHash, request, traceId);
	}

	/**
	 * Fetches one of the user's own top-up transactions.
	 *
	 * @param userId    authenticated user identifier
	 * @param paymentId payment transaction identifier
	 * @return the top-up response for the owned transaction
	 * @throws NotFoundException when the transaction is absent or owned by another user
	 */
	@Transactional(readOnly = true)
	public TopUpResponse fetchPaymentForUser(UUID userId, UUID paymentId) {
		TopupTransaction transaction = topupTransactionRepository.findByIdAndUserId(paymentId, userId)
				.orElseThrow(() -> new NotFoundException("Payment transaction not found"));
		return toTopUpResponse(transaction);
	}

	/**
	 * Reads any payment top-up transaction by id without ownership scoping for admin or support lookups,
	 * auditing the privileged cross-owner read.
	 *
	 * <p>Runs in a read-write transaction so the audit insert is flushed; a read-only transaction sets
	 * Hibernate flush mode to MANUAL and would silently drop the audit row.
	 *
	 * @param adminUserId acting admin principal performing the lookup
	 * @param paymentId   payment transaction identifier
	 * @param traceId     current request trace identifier
	 * @return payment top-up response
	 * @throws NotFoundException if no payment transaction exists for the id
	 */
	@Transactional
	public TopUpResponse fetchPayment(UUID adminUserId, UUID paymentId, String traceId) {
		TopupTransaction transaction = topupTransactionRepository.findById(paymentId)
				.orElseThrow(() -> new NotFoundException("Payment transaction was not found"));
		paymentActivityLogService.logAdminPaymentLookup(adminUserId, transaction, traceId, Instant.now());
		return toTopUpResponse(transaction);
	}

	private IdempotentResponse replayOrConflict(IdempotencyKey existing, String requestHash) {
		if (!Objects.equals(existing.getRequestHash(), requestHash)) {
			throw new IdempotencyConflictException("Idempotency key was already used with a different request");
		}

		return new IdempotentResponse(existing.getResponseStatus(), readStoredBody(existing.getResponseBody()));
	}

	private IdempotentResponse processTopUp(
			UUID userId,
			String idempotencyKey,
			String requestHash,
			TopUpRequest request,
			String traceId) {
		long startedAtNanos = System.nanoTime();
		Instant now = Instant.now();
		TopupTransaction transaction = TopupTransaction.createPending(
				UUID.randomUUID(),
				userId,
				request.walletId(),
				request.amount(),
				idempotencyKey,
				traceId,
				now);
		topupTransactionRepository.save(transaction);
		callGatewayAfterCommit(transaction.getId(), request, traceId);

		TopUpResponse response = toTopUpResponse(transaction);
		paymentActivityLogService.logTopUpCreated(
				transaction,
				request,
				response,
				durationMs(startedAtNanos),
				now);
		storeIdempotency(userId, idempotencyKey, requestHash, HttpStatus.CREATED.value(), response, now);
		return new IdempotentResponse(HttpStatus.CREATED.value(), response);
	}

	private TopUpResponse toTopUpResponse(TopupTransaction transaction) {
		return new TopUpResponse(
				transaction.getId(),
				transaction.getWalletId(),
				transaction.getAmount(),
				transaction.getStatus().name(),
				transaction.getCreatedAt());
	}

	private Long durationMs(long startedAtNanos) {
		return (System.nanoTime() - startedAtNanos) / 1_000_000L;
	}

	private void callGatewayAfterCommit(UUID paymentTransactionId, TopUpRequest request, String traceId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			paymentGatewayClient.requestTopUp(paymentTransactionId, request, traceId);
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				paymentGatewayClient.requestTopUp(paymentTransactionId, request, traceId);
			}
		});
	}

	private void storeIdempotency(
			UUID userId,
			String idempotencyKey,
			String requestHash,
			Integer responseStatus,
			Object responseBody,
			Instant now) {
		idempotencyKeyRepository.save(IdempotencyKey.create(
				UUID.randomUUID(),
				userId,
				idempotencyKey,
				requestHash,
				responseStatus,
				writeJson(responseBody),
				now));
	}

	private String hashRequest(TopUpRequest request) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(writeJson(request).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	private Object readStoredBody(String responseBody) {
		try {
			return objectMapper.readTree(responseBody);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Stored idempotency response is invalid JSON", ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Idempotency response could not be serialized", ex);
		}
	}
}
