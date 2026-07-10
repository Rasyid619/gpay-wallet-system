package com.gpay.auth_service.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gpay.auth_service.exception.InternalAuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for internal auth token validation, covering the configured, unconfigured,
 * and mismatched-token branches.
 */
class InternalUserAuthenticationServiceTest {

	private InternalUserAuthenticationService serviceWithToken(String configuredToken) {
		InternalUserAuthenticationService service = new InternalUserAuthenticationService();
		ReflectionTestUtils.setField(service, "internalToken", configuredToken);
		return service;
	}

	@Test
	void acceptsMatchingToken() {
		InternalUserAuthenticationService service = serviceWithToken("internal-test-token");

		assertThatCode(() -> service.validate("internal-test-token")).doesNotThrowAnyException();
	}

	@Test
	void rejectsMismatchedToken() {
		InternalUserAuthenticationService service = serviceWithToken("internal-test-token");

		assertThatThrownBy(() -> service.validate("wrong-token"))
				.isInstanceOf(InternalAuthenticationException.class);
	}

	@Test
	void rejectsWhenInternalTokenIsNotConfigured() {
		InternalUserAuthenticationService service = serviceWithToken(null);

		assertThatThrownBy(() -> service.validate("any-token"))
				.isInstanceOf(InternalAuthenticationException.class);
	}

	@Test
	void rejectsWhenInternalTokenIsBlank() {
		InternalUserAuthenticationService service = serviceWithToken("   ");

		assertThatThrownBy(() -> service.validate("any-token"))
				.isInstanceOf(InternalAuthenticationException.class);
	}
}
