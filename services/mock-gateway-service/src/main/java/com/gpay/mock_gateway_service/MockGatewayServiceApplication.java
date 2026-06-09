package com.gpay.mock_gateway_service;

import com.gpay.mock_gateway_service.config.MockGatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockGatewayProperties.class)
public class MockGatewayServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MockGatewayServiceApplication.class, args);
	}
}
