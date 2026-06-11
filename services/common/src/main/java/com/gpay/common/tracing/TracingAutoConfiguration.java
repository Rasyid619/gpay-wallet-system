package com.gpay.common.tracing;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the shared trace id servlet filter for any service on the classpath.
 *
 * <p>The {@link TraceIdAuthenticationEntryPoint} is intentionally not registered
 * here; services that use Spring Security construct it in their own security
 * configuration so the web-security test slices keep working without loading
 * this auto-configuration.
 */
@AutoConfiguration
public class TracingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public TraceIdFilter traceIdFilter() {
		return new TraceIdFilter();
	}
}
