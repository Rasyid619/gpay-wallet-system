package com.gpay.payment_service.service;

import com.gpay.payment_service.config.PaymentWebhookProperties;
import com.gpay.payment_service.exception.InvalidWebhookSignatureException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GatewayWebhookSignatureService {

	private static final String HMAC_SHA256 = "HmacSHA256";

	private final PaymentWebhookProperties properties;

	public void validate(String timestamp, String rawRequestBody, String providedSignature) {
		String expectedSignature = sign(timestamp, rawRequestBody);
		if (!MessageDigest.isEqual(
				expectedSignature.getBytes(StandardCharsets.UTF_8),
				providedSignature.getBytes(StandardCharsets.UTF_8))) {
			throw new InvalidWebhookSignatureException("Gateway webhook signature is invalid");
		}
	}

	public String sign(String timestamp, String rawRequestBody) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(properties.gatewaySecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			byte[] signature = mac.doFinal((timestamp + "." + rawRequestBody).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(signature);
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to sign gateway webhook payload", ex);
		}
	}
}
