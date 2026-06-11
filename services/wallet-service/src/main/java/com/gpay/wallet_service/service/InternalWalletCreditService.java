package com.gpay.wallet_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.wallet_service.constant.LedgerEntrySource;
import com.gpay.wallet_service.constant.LedgerEntryType;
import com.gpay.wallet_service.dto.IdempotentResponse;
import com.gpay.wallet_service.dto.InternalWalletCreditRequest;
import com.gpay.wallet_service.dto.InternalWalletCreditResponse;
import com.gpay.wallet_service.entity.ActivityLog;
import com.gpay.wallet_service.entity.IdempotencyKey;
import com.gpay.wallet_service.entity.LedgerEntry;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.exception.BadRequestException;
import com.gpay.wallet_service.exception.IdempotencyConflictException;
import com.gpay.wallet_service.exception.PaymentTransactionConflictException;
import com.gpay.wallet_service.exception.WalletNotFoundException;
import com.gpay.wallet_service.repository.ActivityLogRepository;
import com.gpay.wallet_service.repository.IdempotencyKeyRepository;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Credits wallets from trusted internal payment-service delivery. */
@Service
@RequiredArgsConstructor
public class InternalWalletCreditService {

	private static final String ACTION_CREDIT = "INTERNAL_WALLET_CREDIT";
	private static final String SERVICE_NAME = "wallet-service";

	private final ActivityLogRepository activityLogRepository;
	private final IdempotencyKeyRepository idempotencyKeyRepository;
	private final InternalWalletAuthenticationService internalWalletAuthenticationService;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final ObjectMapper objectMapper;
	private final WalletRepository walletRepository;

	/**
	 * Creates or replays an internal wallet credit.
	 *
	 * @param providedInternalToken internal service token from the request header
	 * @param idempotencyKey        client-provided durable idempotency key
	 * @param request               wallet credit request
	 * @param traceId               request trace identifier when supplied
	 * @return status and body for the credit response
	 */
	@Transactional
	public IdempotentResponse credit(
			String providedInternalToken,
			String idempotencyKey,
			InternalWalletCreditRequest request,
			String traceId) {
		internalWalletAuthenticationService.validate(providedInternalToken);
		validateIdempotencyKey(idempotencyKey);

		String requestHash = hashRequest(request);
		Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return replayOrConflict(existing.get(), requestHash);
		}

		Optional<LedgerEntry> existingCredit = ledgerEntryRepository.findByPaymentTransactionId(
				request.paymentTransactionId());
		if (existingCredit.isPresent()) {
			return replayPaymentTransactionOrConflict(idempotencyKey, requestHash, request, existingCredit.get());
		}

		return processCredit(idempotencyKey, requestHash, request, traceId);
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BadRequestException("Idempotency-Key header is required");
		}
	}

	private IdempotentResponse replayOrConflict(IdempotencyKey existing, String requestHash) {
		if (!existing.getRequestHash().equals(requestHash)) {
			throw new IdempotencyConflictException("Idempotency key was already used with a different request");
		}

		return new IdempotentResponse(existing.getResponseStatus(), readStoredBody(existing.getResponseBody()));
	}

	private IdempotentResponse replayPaymentTransactionOrConflict(
			String idempotencyKey,
			String requestHash,
			InternalWalletCreditRequest request,
			LedgerEntry existingCredit) {
		boolean hasSameWallet = existingCredit.getWallet().getId().equals(request.walletId());
		boolean hasSameAmount = existingCredit.getAmount().equals(request.amount());
		if (!hasSameWallet || !hasSameAmount) {
			throw new PaymentTransactionConflictException(
					"Payment transaction was already credited with a different request");
		}

		InternalWalletCreditResponse response = new InternalWalletCreditResponse(
				existingCredit.getWallet().getId(),
				request.paymentTransactionId(),
				existingCredit.getAmount(),
				existingCredit.getBalanceAfter(),
				existingCredit.getCreatedAt());
		storeIdempotency(idempotencyKey, null, requestHash, HttpStatus.OK.value(), response, Instant.now());
		return new IdempotentResponse(HttpStatus.OK.value(), response);
	}

	private IdempotentResponse processCredit(
			String idempotencyKey,
			String requestHash,
			InternalWalletCreditRequest request,
			String traceId) {
		Instant now = Instant.now();
		Wallet wallet = walletRepository.findByIdForUpdate(request.walletId())
				.orElseThrow(() -> new WalletNotFoundException("Wallet was not found"));

		wallet.credit(request.amount(), now);
		walletRepository.save(wallet);

		ledgerEntryRepository.save(LedgerEntry.create(
				UUID.randomUUID(),
				wallet,
				null,
				request.paymentTransactionId(),
				LedgerEntryType.CREDIT,
				LedgerEntrySource.TOP_UP,
				request.amount(),
				wallet.getBalance(),
				"Top-up received",
				now));
		activityLogRepository.save(ActivityLog.create(
				UUID.randomUUID(),
				wallet.getUserId(),
				wallet,
				ACTION_CREDIT,
				"SUCCESS",
				traceId,
				SERVICE_NAME,
				null,
				now));

		InternalWalletCreditResponse response = new InternalWalletCreditResponse(
				wallet.getId(),
				request.paymentTransactionId(),
				request.amount(),
				wallet.getBalance(),
				now);
		storeIdempotency(idempotencyKey, null, requestHash, HttpStatus.OK.value(), response, now);
		return new IdempotentResponse(HttpStatus.OK.value(), response);
	}

	private void storeIdempotency(
			String idempotencyKey,
			UUID userId,
			String requestHash,
			Integer responseStatus,
			Object responseBody,
			Instant now) {
		idempotencyKeyRepository.save(IdempotencyKey.create(
				UUID.randomUUID(),
				idempotencyKey,
				userId,
				requestHash,
				responseStatus,
				writeJson(responseBody),
				now));
	}

	private String hashRequest(InternalWalletCreditRequest request) {
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
			throw new IllegalStateException("Unable to serialize idempotency response", ex);
		}
	}
}
