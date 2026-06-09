package com.gpay.mock_gateway_service.service;

import com.gpay.mock_gateway_service.config.MockGatewayProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GatewaySignatureService {

	private static final String HMAC_SHA256 = "HmacSHA256";

	private final MockGatewayProperties properties;

	public String sign(String timestamp, String rawRequestBody) {
		if (!StringUtils.hasText(properties.getWebhookSecret())) {
			throw new IllegalStateException("GATEWAY_WEBHOOK_SECRET must be configured");
		}

		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			byte[] signature = mac.doFinal((timestamp + "." + rawRequestBody).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(signature);
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to sign gateway webhook payload", ex);
		}
	}
}
