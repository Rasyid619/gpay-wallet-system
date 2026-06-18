package com.gpay.wallet_service.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gpay.wallet_service.exception.InternalAuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for internal wallet token validation, covering the configured, unconfigured,
 * and mismatched-token branches.
 */
class InternalWalletAuthenticationServiceTest {

	private InternalWalletAuthenticationService serviceWithToken(String configuredToken) {
		InternalWalletAuthenticationService service = new InternalWalletAuthenticationService();
		ReflectionTestUtils.setField(service, "internalToken", configuredToken);
		return service;
	}

	@Test
	void acceptsMatchingToken() {
		InternalWalletAuthenticationService service = serviceWithToken("internal-test-token");

		assertThatCode(() -> service.validate("internal-test-token")).doesNotThrowAnyException();
	}

	@Test
	void rejectsMismatchedToken() {
		InternalWalletAuthenticationService service = serviceWithToken("internal-test-token");

		assertThatThrownBy(() -> service.validate("wrong-token"))
				.isInstanceOf(InternalAuthenticationException.class);
	}

	@Test
	void rejectsWhenInternalTokenIsNotConfigured() {
		InternalWalletAuthenticationService service = serviceWithToken(null);

		assertThatThrownBy(() -> service.validate("any-token"))
				.isInstanceOf(InternalAuthenticationException.class);
	}

	@Test
	void rejectsWhenInternalTokenIsBlank() {
		InternalWalletAuthenticationService service = serviceWithToken("   ");

		assertThatThrownBy(() -> service.validate("any-token"))
				.isInstanceOf(InternalAuthenticationException.class);
	}
}
