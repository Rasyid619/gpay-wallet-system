package com.gpay.payment_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.payment_service.constant.OutboxEventType;
import com.gpay.payment_service.dto.WalletCreditOutboxPayload;
import com.gpay.payment_service.entity.OutboxEvent;
import com.gpay.payment_service.entity.TopupTransaction;
import com.gpay.payment_service.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentOutboxService {

	private final ObjectMapper objectMapper;
	private final OutboxEventRepository outboxEventRepository;

	public void enqueueWalletCredit(TopupTransaction transaction, Instant now) {
		if (outboxEventRepository.existsByAggregateIdAndEventType(
				transaction.getId(),
				OutboxEventType.CREDIT_WALLET_REQUESTED)) {
			return;
		}

		WalletCreditOutboxPayload payload = new WalletCreditOutboxPayload(
				transaction.getWalletId(),
				transaction.getId(),
				transaction.getAmount());
		outboxEventRepository.save(OutboxEvent.createPending(
				UUID.randomUUID(),
				OutboxEventType.CREDIT_WALLET_REQUESTED,
				transaction.getId(),
				writeJson(payload),
				now));
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Outbox payload could not be serialized", ex);
		}
	}
}
