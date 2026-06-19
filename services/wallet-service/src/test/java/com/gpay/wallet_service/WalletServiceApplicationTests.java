package com.gpay.wallet_service;

import com.gpay.wallet_service.support.WalletPostgresContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/* Verifies the wallet service Spring context can start. */
@SpringBootTest
class WalletServiceApplicationTests {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		WalletPostgresContainer.registerProperties(registry);
	}

	@Test
	void contextLoads() {
	}
}
