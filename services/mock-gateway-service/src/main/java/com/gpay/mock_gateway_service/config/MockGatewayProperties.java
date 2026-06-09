package com.gpay.mock_gateway_service.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "mock-gateway")
public class MockGatewayProperties {

	@NotNull
	@Positive
	private Long timeoutDelayMs;
	@NotNull
	private URI paymentWebhookUrl;
	@NotBlank
	private String webhookSecret = "";
}
