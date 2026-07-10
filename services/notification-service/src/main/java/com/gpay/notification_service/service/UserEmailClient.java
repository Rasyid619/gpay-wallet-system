package com.gpay.notification_service.service;

import com.gpay.common.tracing.TraceIdContext;
import com.gpay.notification_service.config.NotificationAuthProperties;
import com.gpay.notification_service.dto.UserEmailLookupResponse;
import com.gpay.notification_service.exception.EmailDeliveryException;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/* Resolves recipient email addresses through the auth-service internal lookup. */
@Slf4j
@Service
public class UserEmailClient {

	private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
	private static final String TRACE_ID_HEADER = "X-Trace-Id";

	private final NotificationAuthProperties properties;
	private final WebClient webClient;

	public UserEmailClient(NotificationAuthProperties properties, WebClient.Builder webClientBuilder) {
		this.properties = properties;
		this.webClient = webClientBuilder.build();
	}

	/**
	 * Fetches the registered email address of a user.
	 *
	 * @param userId auth-service user identifier
	 * @return registered email address
	 * @throws NonRetryableNotificationException when the user does not exist
	 * @throws EmailDeliveryException            when auth-service is unreachable
	 */
	public String fetchEmail(UUID userId) {
		try {
			UserEmailLookupResponse response = webClient
					.get()
					.uri(properties.userLookupUrl() + "/" + userId)
					.header(INTERNAL_TOKEN_HEADER, properties.internalToken())
					.headers(headers -> {
						String traceId = TraceIdContext.getTraceId();
						if (StringUtils.hasText(traceId)) {
							headers.add(TRACE_ID_HEADER, traceId);
						}
					})
					.retrieve()
					.bodyToMono(UserEmailLookupResponse.class)
					.timeout(Duration.ofMillis(properties.timeoutMs()))
					.block();
			if (response == null || !StringUtils.hasText(response.email())) {
				throw new NonRetryableNotificationException("User lookup returned no email for user " + userId);
			}
			return response.email();
		} catch (WebClientResponseException.NotFound ex) {
			throw new NonRetryableNotificationException("User was not found for notification recipient", ex);
		} catch (NonRetryableNotificationException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			throw new EmailDeliveryException("Recipient email lookup failed", ex);
		}
	}
}
