package com.gpay.auth_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpay.auth_service.security.JwtAuthFilter;
import com.gpay.common.tracing.TraceIdAccessDeniedHandler;
import com.gpay.common.tracing.TraceIdAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/* Spring Security configuration for the auth service. */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAuthFilter jwtAuthFilter;

	@Bean
	public SecurityFilterChain filterChain(
			HttpSecurity http,
			TraceIdAuthenticationEntryPoint authenticationEntryPoint,
			TraceIdAccessDeniedHandler accessDeniedHandler) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/auth/login", "/auth/register", "/auth/refresh").permitAll()
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public TraceIdAuthenticationEntryPoint traceIdAuthenticationEntryPoint(ObjectMapper objectMapper) {
		return new TraceIdAuthenticationEntryPoint(objectMapper);
	}

	@Bean
	public TraceIdAccessDeniedHandler traceIdAccessDeniedHandler(ObjectMapper objectMapper) {
		return new TraceIdAccessDeniedHandler(objectMapper);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
