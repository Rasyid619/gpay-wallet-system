package com.gpay.auth_service.service;

import com.gpay.auth_service.config.AuthWalletProperties;
import com.gpay.auth_service.dto.WalletProvisionRequest;
import com.gpay.auth_service.exception.WalletProvisioningException;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/* Calls Wallet Service to provision wallets for newly registered users. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletProvisioningClient {

	private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
	private static final String TRACE_ID_HEADER = "X-Trace-Id";

	private final AuthWalletProperties properties;
	private final WebClient.Builder webClientBuilder;

	/**
	 * Provisions a wallet for the supplied auth-service user.
	 *
	 * @param userId  registered user identifier
	 * @param traceId request trace identifier when supplied
	 */
	public void provisionWallet(UUID userId, String traceId) {
		try {
			log.info(
					"Provisioning wallet for registered user userId={} traceId={}",
					userId,
					traceId);
			webClientBuilder.build()
					.post()
					.uri(properties.provisionUrl())
					.contentType(MediaType.APPLICATION_JSON)
					.header(INTERNAL_TOKEN_HEADER, properties.internalToken())
					.headers(headers -> {
						if (StringUtils.hasText(traceId)) {
							headers.add(TRACE_ID_HEADER, traceId);
						}
					})
					.bodyValue(new WalletProvisionRequest(userId))
					.retrieve()
					.toBodilessEntity()
					.timeout(Duration.ofMillis(properties.provisionTimeoutMs()))
					.block();
			log.info(
					"Provisioned wallet for registered user userId={} traceId={}",
					userId,
					traceId);
		} catch (RuntimeException ex) {
			throw new WalletProvisioningException("Wallet could not be provisioned for registered user", ex);
		}
	}
}
