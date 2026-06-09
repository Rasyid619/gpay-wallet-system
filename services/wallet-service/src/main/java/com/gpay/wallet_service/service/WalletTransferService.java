package com.gpay.wallet_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.wallet_service.constant.LedgerEntrySource;
import com.gpay.wallet_service.constant.LedgerEntryType;
import com.gpay.wallet_service.constant.TransferStatus;
import com.gpay.wallet_service.dto.IdempotentResponse;
import com.gpay.wallet_service.dto.TransferRequest;
import com.gpay.wallet_service.dto.TransferResponse;
import com.gpay.wallet_service.entity.ActivityLog;
import com.gpay.wallet_service.entity.IdempotencyKey;
import com.gpay.wallet_service.entity.LedgerEntry;
import com.gpay.wallet_service.entity.Transfer;
import com.gpay.wallet_service.entity.Wallet;
import com.gpay.wallet_service.exception.BadRequestException;
import com.gpay.wallet_service.exception.IdempotencyConflictException;
import com.gpay.wallet_service.repository.ActivityLogRepository;
import com.gpay.wallet_service.repository.IdempotencyKeyRepository;
import com.gpay.wallet_service.repository.LedgerEntryRepository;
import com.gpay.wallet_service.repository.TransferRepository;
import com.gpay.wallet_service.repository.WalletRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Executes authenticated wallet-to-wallet transfers. */
@Service
public class WalletTransferService {

	private static final String ACTION_TRANSFER = "WALLET_TRANSFER";

	private final ActivityLogRepository activityLogRepository;
	private final IdempotencyKeyRepository idempotencyKeyRepository;
	private final LedgerEntryRepository ledgerEntryRepository;
	private final ObjectMapper objectMapper;
	private final TransferRepository transferRepository;
	private final WalletRepository walletRepository;
	private final Long maxDailyTransferAmount;

	public WalletTransferService(
			ActivityLogRepository activityLogRepository,
			IdempotencyKeyRepository idempotencyKeyRepository,
			LedgerEntryRepository ledgerEntryRepository,
			ObjectMapper objectMapper,
			TransferRepository transferRepository,
			WalletRepository walletRepository,
			@Value("${wallet.transfer.max-daily-transfer-amount}") Long maxDailyTransferAmount) {
		this.activityLogRepository = activityLogRepository;
		this.idempotencyKeyRepository = idempotencyKeyRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
		this.objectMapper = objectMapper;
		this.transferRepository = transferRepository;
		this.walletRepository = walletRepository;
		this.maxDailyTransferAmount = maxDailyTransferAmount;
	}

	/**
	 * Creates or replays an idempotent wallet transfer response.
	 *
	 * @param userId         authenticated user identifier
	 * @param idempotencyKey client-provided durable idempotency key
	 * @param request        transfer request payload
	 * @param traceId        request trace identifier when supplied
	 * @return status and body for the transfer response
	 */
	@Transactional
	public IdempotentResponse transfer(
			UUID userId,
			String idempotencyKey,
			TransferRequest request,
			String traceId) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BadRequestException("Idempotency-Key header is required");
		}

		String requestHash = hashRequest(request);
		Optional<IdempotencyKey> existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return replayOrConflict(existing.get(), userId, requestHash);
		}

		return processTransfer(userId, idempotencyKey, requestHash, request, traceId);
	}

	private IdempotentResponse replayOrConflict(IdempotencyKey existing, UUID userId, String requestHash) {
		boolean hasSameUser = Objects.equals(existing.getUserId(), userId);
		boolean hasSameRequest = existing.getRequestHash().equals(requestHash);
		if (!hasSameUser || !hasSameRequest) {
			throw new IdempotencyConflictException("Idempotency key was already used with a different request");
		}

		return new IdempotentResponse(existing.getResponseStatus(), readStoredBody(existing.getResponseBody()));
	}

	private IdempotentResponse processTransfer(
			UUID userId,
			String idempotencyKey,
			String requestHash,
			TransferRequest request,
			String traceId) {
		Instant now = Instant.now();
		Optional<Wallet> senderWalletReference = walletRepository.findByUserId(userId);
		if (senderWalletReference.isEmpty()) {
			return storeErrorResponse(
					idempotencyKey,
					userId,
					requestHash,
					null,
					HttpStatus.NOT_FOUND,
					"WALLET_NOT_FOUND",
					"Wallet was not found for authenticated user",
					traceId,
					now);
		}

		Wallet senderWalletReferenceValue = senderWalletReference.get();
		if (senderWalletReferenceValue.getId().equals(request.receiverWalletId())) {
			return storeErrorResponse(
					idempotencyKey,
					userId,
					requestHash,
					senderWalletReferenceValue,
					HttpStatus.BAD_REQUEST,
					"SAME_WALLET_TRANSFER",
					"Sender and receiver wallet must be different",
					traceId,
					now);
		}

		List<Wallet> lockedWallets = walletRepository.findAllByIdInForUpdate(List.of(
				senderWalletReferenceValue.getId(),
				request.receiverWalletId()));
		if (lockedWallets.size() != 2) {
			return storeErrorResponse(
					idempotencyKey,
					userId,
					requestHash,
					senderWalletReferenceValue,
					HttpStatus.NOT_FOUND,
					"WALLET_NOT_FOUND",
					"Receiver wallet was not found",
					traceId,
					now);
		}

		Wallet senderWallet = resolveWallet(lockedWallets, senderWalletReferenceValue.getId());
		Wallet receiverWallet = resolveWallet(lockedWallets, request.receiverWalletId());

		Long sentToday = transferRepository.sumSuccessfulAmountBySenderWalletIdBetween(
				senderWallet.getId(),
				startOfToday(now),
				startOfTomorrow(now));
		if (sentToday + request.amount() > maxDailyTransferAmount) {
			return storeFailedTransferResponse(
					idempotencyKey,
					userId,
					requestHash,
					senderWallet,
					receiverWallet,
					request,
					"DAILY_TRANSFER_LIMIT_EXCEEDED",
					"Daily transfer limit exceeded",
					traceId,
					now);
		}

		if (senderWallet.getBalance() < request.amount()) {
			return storeFailedTransferResponse(
					idempotencyKey,
					userId,
					requestHash,
					senderWallet,
					receiverWallet,
					request,
					"INSUFFICIENT_BALANCE",
					"Wallet balance is not enough for this transfer",
					traceId,
					now);
		}

		return storeSuccessfulTransferResponse(
				idempotencyKey,
				userId,
				requestHash,
				senderWallet,
				receiverWallet,
				request,
				traceId,
				now);
	}

	private Wallet resolveWallet(List<Wallet> wallets, UUID walletId) {
		return wallets.stream()
				.filter(wallet -> wallet.getId().equals(walletId))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Locked wallet was not found"));
	}

	private IdempotentResponse storeSuccessfulTransferResponse(
			String idempotencyKey,
			UUID userId,
			String requestHash,
			Wallet senderWallet,
			Wallet receiverWallet,
			TransferRequest request,
			String traceId,
			Instant now) {
		Transfer transfer = transferRepository.save(Transfer.create(
				UUID.randomUUID(),
				senderWallet,
				receiverWallet,
				request.amount(),
				TransferStatus.SUCCESS,
				null,
				now));

		senderWallet.debit(request.amount(), now);
		receiverWallet.credit(request.amount(), now);
		walletRepository.save(senderWallet);
		walletRepository.save(receiverWallet);

		ledgerEntryRepository.save(LedgerEntry.create(
				UUID.randomUUID(),
				senderWallet,
				transfer,
				null,
				LedgerEntryType.DEBIT,
				LedgerEntrySource.TRANSFER,
				request.amount(),
				senderWallet.getBalance(),
				"Transfer sent",
				now));
		ledgerEntryRepository.save(LedgerEntry.create(
				UUID.randomUUID(),
				receiverWallet,
				transfer,
				null,
				LedgerEntryType.CREDIT,
				LedgerEntrySource.TRANSFER,
				request.amount(),
				receiverWallet.getBalance(),
				"Transfer received",
				now));

		TransferResponse response = new TransferResponse(
				transfer.getId(),
				senderWallet.getId(),
				receiverWallet.getId(),
				request.amount(),
				transfer.getStatus().name(),
				now);
		storeActivityLog(userId, senderWallet, "SUCCESS", traceId, now);
		storeIdempotency(idempotencyKey, userId, requestHash, HttpStatus.OK.value(), response, now);
		return new IdempotentResponse(HttpStatus.OK.value(), response);
	}

	private IdempotentResponse storeFailedTransferResponse(
			String idempotencyKey,
			UUID userId,
			String requestHash,
			Wallet senderWallet,
			Wallet receiverWallet,
			TransferRequest request,
			String error,
			String message,
			String traceId,
			Instant now) {
		transferRepository.save(Transfer.create(
				UUID.randomUUID(),
				senderWallet,
				receiverWallet,
				request.amount(),
				TransferStatus.FAILED,
				error,
				now));
		return storeErrorResponse(
				idempotencyKey,
				userId,
				requestHash,
				senderWallet,
				HttpStatus.BAD_REQUEST,
				error,
				message,
				traceId,
				now);
	}

	private IdempotentResponse storeErrorResponse(
			String idempotencyKey,
			UUID userId,
			String requestHash,
			Wallet wallet,
			HttpStatus status,
			String error,
			String message,
			String traceId,
			Instant now) {
		Map<String, String> response = Map.of("error", error, "message", message);
		storeActivityLog(userId, wallet, error, traceId, now);
		storeIdempotency(idempotencyKey, userId, requestHash, status.value(), response, now);
		return new IdempotentResponse(status.value(), response);
	}

	private void storeActivityLog(
			UUID userId,
			Wallet wallet,
			String status,
			String traceId,
			Instant now) {
		activityLogRepository.save(ActivityLog.create(
				UUID.randomUUID(),
				userId,
				wallet,
				ACTION_TRANSFER,
				status,
				traceId,
				null,
				now));
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

	private Instant startOfToday(Instant now) {
		return LocalDate.ofInstant(now, ZoneId.systemDefault())
				.atStartOfDay(ZoneId.systemDefault())
				.toInstant();
	}

	private Instant startOfTomorrow(Instant now) {
		return LocalDate.ofInstant(now, ZoneId.systemDefault())
				.plusDays(1)
				.atStartOfDay(ZoneId.systemDefault())
				.toInstant();
	}

	private String hashRequest(TransferRequest request) {
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
