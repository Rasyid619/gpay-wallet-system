package com.gpay.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gpay.notification_service.config.NotificationAuthProperties;
import com.gpay.notification_service.exception.EmailDeliveryException;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Unit tests for the auth-service email lookup client, covering the resolved
 * email, unknown-user, empty-email, and unreachable-service classifications.
 */
class UserEmailClientTest {

	private final ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);

	private UserEmailClient client() {
		WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
		return new UserEmailClient(
				new NotificationAuthProperties("http://localhost:8081/internal/users", "internal-token", 5000L),
				builder);
	}

	private void stubResponse(HttpStatus status, String body) {
		ClientResponse response = ClientResponse.create(status)
				.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				.body(body)
				.build();
		when(exchangeFunction.exchange(any())).thenReturn(Mono.just(response));
	}

	@Test
	void returnsResolvedEmailForKnownUser() {
		UUID userId = UUID.randomUUID();
		stubResponse(HttpStatus.OK, "{\"id\":\"" + userId + "\",\"email\":\"recipient@example.com\"}");

		assertThat(client().fetchEmail(userId)).isEqualTo("recipient@example.com");
	}

	@Test
	void classifiesUnknownUserAsNonRetryable() {
		stubResponse(HttpStatus.NOT_FOUND, "{\"error\":\"NOT_FOUND\"}");

		assertThatThrownBy(() -> client().fetchEmail(UUID.randomUUID()))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void classifiesBlankEmailResponseAsNonRetryable() {
		UUID userId = UUID.randomUUID();
		stubResponse(HttpStatus.OK, "{\"id\":\"" + userId + "\",\"email\":\"\"}");

		assertThatThrownBy(() -> client().fetchEmail(userId))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void classifiesEmptyLookupResponseAsNonRetryable() {
		when(exchangeFunction.exchange(any()))
				.thenReturn(Mono.just(ClientResponse.create(HttpStatus.OK).build()));

		assertThatThrownBy(() -> client().fetchEmail(UUID.randomUUID()))
				.isInstanceOf(NonRetryableNotificationException.class);
	}

	@Test
	void classifiesServerErrorAsRetryable() {
		stubResponse(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"INTERNAL\"}");

		assertThatThrownBy(() -> client().fetchEmail(UUID.randomUUID()))
				.isInstanceOf(EmailDeliveryException.class);
	}
}
