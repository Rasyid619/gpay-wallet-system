package com.gpay.mock_gateway_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.mock_gateway_service.config.MockGatewayProperties;
import com.gpay.mock_gateway_service.dto.GatewayWebhookPayload;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookClient {

	private static final String GATEWAY_SIGNATURE_HEADER = "X-Gateway-Signature";
	private static final String GATEWAY_TIMESTAMP_HEADER = "X-Gateway-Timestamp";
	private static final String TRACE_ID_HEADER = "X-Trace-Id";

	private final WebClient.Builder webClientBuilder;
	private final ObjectMapper objectMapper;
	private final MockGatewayProperties properties;
	private final GatewaySignatureService gatewaySignatureService;

	public void send(GatewayWebhookPayload payload, String traceId) {
		if (properties.getPaymentWebhookUrl() == null) {
			throw new IllegalStateException("PAYMENT_WEBHOOK_URL must be configured");
		}

		String rawRequestBody = serialize(payload);
		String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
		String signature = gatewaySignatureService.sign(timestamp, rawRequestBody);

		try {
			webClientBuilder.build()
					.post()
					.uri(properties.getPaymentWebhookUrl())
					.contentType(MediaType.APPLICATION_JSON)
					.header(GATEWAY_SIGNATURE_HEADER, signature)
					.header(GATEWAY_TIMESTAMP_HEADER, timestamp)
					.headers(headers -> addTraceId(headers, traceId))
					.body(BodyInserters.fromValue(rawRequestBody))
					.exchangeToMono(response -> response.releaseBody())
					.block();
		} catch (WebClientRequestException ex) {
			log.warn("Payment webhook callback could not reach payment service", ex);
		}
	}

	private String serialize(GatewayWebhookPayload payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize gateway webhook payload", ex);
		}
	}

	private void addTraceId(HttpHeaders headers, String traceId) {
		if (StringUtils.hasText(traceId)) {
			headers.add(TRACE_ID_HEADER, traceId);
		}
	}
}
