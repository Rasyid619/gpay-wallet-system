package com.gpay.payment_service.service;

import com.gpay.payment_service.config.PaymentOutboxProperties;
import com.gpay.payment_service.dto.WalletCreditOutboxPayload;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class WalletCreditClient {

	private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
	private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
	private static final String TRACE_ID_HEADER = "X-Trace-Id";

	private final PaymentOutboxProperties properties;
	private final WebClient.Builder webClientBuilder;

	public void creditWallet(WalletCreditOutboxPayload payload, String idempotencyKey, String traceId) {
		webClientBuilder.build()
				.post()
				.uri(properties.walletCreditUrl())
				.contentType(MediaType.APPLICATION_JSON)
				.header(INTERNAL_TOKEN_HEADER, properties.walletInternalToken())
				.header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
				.headers(headers -> {
					if (StringUtils.hasText(traceId)) {
						headers.add(TRACE_ID_HEADER, traceId);
					}
				})
				.bodyValue(payload)
				.retrieve()
				.toBodilessEntity()
				.timeout(Duration.ofMillis(properties.requestTimeoutMs()))
				.block();
	}
}
