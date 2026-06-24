package com.gpay.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers the shared JWT validation filter and service for any service that
 * configures a {@code jwt.secret}.
 *
 * <p>The {@link ConditionalOnProperty} guard keeps this auto-configuration inert
 * for services with no JWT (such as mock-gateway-service) so they do not require
 * a secret. Token generation stays in auth-service and is intentionally not
 * provided here.
 *
 * <p>The validation bean is named {@code jwtValidationService} so it does not
 * collide with auth-service's own token-generating {@code jwtService} component,
 * which would otherwise take the same default bean name.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "jwt.secret")
public class SecurityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public JwtService jwtValidationService(@Value("${jwt.secret}") String secret) {
		return new JwtService(secret);
	}

	@Bean
	@ConditionalOnMissingBean
	public JwtAuthFilter jwtAuthFilter(JwtService jwtValidationService) {
		return new JwtAuthFilter(jwtValidationService);
	}
}
