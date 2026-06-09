package com.gpay.payment_service.service;

import com.gpay.payment_service.config.PaymentGatewayProperties;
import com.gpay.payment_service.dto.GatewayTopUpRequest;
import com.gpay.payment_service.dto.TopUpRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Exceptions;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGatewayClient {

	private static final String TRACE_ID_HEADER = "X-Trace-Id";

	private final PaymentGatewayProperties properties;
	private final WebClient.Builder webClientBuilder;

	public void requestTopUp(UUID paymentTransactionId, TopUpRequest request, String traceId) {
		GatewayTopUpRequest gatewayRequest = new GatewayTopUpRequest(
				paymentTransactionId,
				request.walletId(),
				request.amount(),
				request.gatewayMode());

		try {
			webClientBuilder.build()
					.post()
					.uri(properties.topUpUrl())
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> {
						if (StringUtils.hasText(traceId)) {
							headers.add(TRACE_ID_HEADER, traceId);
						}
					})
					.bodyValue(gatewayRequest)
					.exchangeToMono(response -> response.releaseBody())
					.timeout(Duration.ofMillis(properties.timeoutMs()))
					.block();
		} catch (RuntimeException ex) {
			if (isTimeout(ex)) {
				log.warn("Mock gateway top-up request timed out for paymentTransactionId={}", paymentTransactionId);
				return;
			}
			if (ex instanceof WebClientRequestException) {
				log.warn("Mock gateway top-up request failed for paymentTransactionId={}", paymentTransactionId, ex);
				return;
			}
			throw ex;
		}
	}

	private boolean isTimeout(RuntimeException ex) {
		Throwable unwrapped = Exceptions.unwrap(ex);
		return unwrapped instanceof TimeoutException;
	}
}
