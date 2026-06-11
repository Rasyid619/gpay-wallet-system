package com.gpay.auth_service;

import com.gpay.auth_service.config.AuthWalletProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/* Bootstraps the auth service application. */
@SpringBootApplication
@EnableConfigurationProperties(AuthWalletProperties.class)
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
