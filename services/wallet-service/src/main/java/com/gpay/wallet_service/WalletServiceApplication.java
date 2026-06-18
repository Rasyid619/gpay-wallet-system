package com.gpay.wallet_service;

import com.gpay.wallet_service.config.WalletKafkaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/* Bootstraps the wallet service application. */
@SpringBootApplication
@EnableConfigurationProperties(WalletKafkaProperties.class)
public class WalletServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WalletServiceApplication.class, args);
	}
}
