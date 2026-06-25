package com.gpay.wallet_service.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gpay.wallet_service.config.WalletReconciliationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the reconciliation scheduler, covering the enabled and disabled config branches.
 */
@ExtendWith(MockitoExtension.class)
class WalletReconciliationSchedulerTest {

	@Mock
	private WalletReconciliationService reconciliationService;

	@Test
	void runsReconciliationWhenEnabled() {
		WalletReconciliationProperties properties = new WalletReconciliationProperties(true, "0 0 * * * *");
		WalletReconciliationScheduler scheduler =
				new WalletReconciliationScheduler(properties, reconciliationService);

		scheduler.runScheduledReconciliation();

		verify(reconciliationService).runReconciliation();
	}

	@Test
	void skipsReconciliationWhenDisabled() {
		WalletReconciliationProperties properties = new WalletReconciliationProperties(false, "0 0 * * * *");
		WalletReconciliationScheduler scheduler =
				new WalletReconciliationScheduler(properties, reconciliationService);

		scheduler.runScheduledReconciliation();

		verifyNoInteractions(reconciliationService);
	}
}
