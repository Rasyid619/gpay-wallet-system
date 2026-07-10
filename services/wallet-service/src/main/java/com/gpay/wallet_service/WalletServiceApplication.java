package com.gpay.wallet_service;

import com.gpay.wallet_service.config.WalletKafkaProperties;
import com.gpay.wallet_service.config.WalletOutboxProperties;
import com.gpay.wallet_service.config.WalletReconciliationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/* Bootstraps the wallet service application. */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
		WalletKafkaProperties.class,
		WalletOutboxProperties.class,
		WalletReconciliationProperties.class
})
public class WalletServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WalletServiceApplication.class, args);
	}
}
