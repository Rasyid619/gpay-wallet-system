package com.gpay.payment_service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/* Time-related application configuration. */
@Configuration
public class TimeConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
