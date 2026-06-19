package com.gpay.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gpay.payment_service.constant.PaymentGatewayMode;
import com.gpay.payment_service.dto.IdempotentResponse;
import com.gpay.payment_service.dto.TopUpRequest;
import com.gpay.payment_service.exception.BadRequestException;
import com.gpay.payment_service.repository.IdempotencyKeyRepository;
import com.gpay.payment_service.repository.TopupTransactionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Branch-focused unit tests for {@link PaymentTopUpService} exercising the blank
 * idempotency-key guard and the synchronous gateway dispatch taken when no
 * transaction synchronization is active.
 */
@ExtendWith(MockitoExtension.class)
class PaymentTopUpServiceBranchTest {

	@Mock
	private IdempotencyKeyRepository idempotencyKeyRepository;

	@Mock
	private PaymentActivityLogService paymentActivityLogService;

	@Mock
	private PaymentGatewayClient paymentGatewayClient;

	@Mock
	private PaymentRateLimiter paymentRateLimiter;

	@Mock
	private TopupTransactionRepository topupTransactionRepository;

	private PaymentTopUpService service() {
		return new PaymentTopUpService(
				idempotencyKeyRepository,
				new ObjectMapper().registerModule(new JavaTimeModule()),
				paymentActivityLogService,
				paymentGatewayClient,
				paymentRateLimiter,
				topupTransactionRepository);
	}

	private TopUpRequest request() {
		return new TopUpRequest(UUID.randomUUID(), 75_000L, PaymentGatewayMode.SUCCESS);
	}

	@Test
	void rejectsBlankIdempotencyKey() {
		assertThatThrownBy(() -> service().topUp(UUID.randomUUID(), "  ", request(), "trace"))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Idempotency-Key header is required");

		verifyNoInteractions(paymentRateLimiter);
	}

	@Test
	void rejectsNullIdempotencyKey() {
		assertThatThrownBy(() -> service().topUp(UUID.randomUUID(), null, request(), "trace"))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Idempotency-Key header is required");

		verifyNoInteractions(paymentRateLimiter);
	}

	@Test
	void dispatchesGatewayImmediatelyWhenNoTransactionSynchronizationIsActive() {
		UUID userId = UUID.randomUUID();
		TopUpRequest request = request();
		when(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(eq(userId), eq("key-no-sync")))
				.thenReturn(Optional.empty());

		IdempotentResponse response = service().topUp(userId, "key-no-sync", request, "trace-no-sync");

		assertThat(response.status()).isEqualTo(201);
		verify(paymentGatewayClient).requestTopUp(any(UUID.class), eq(request), eq("trace-no-sync"));
	}
}
