package com.gpay.auth_service.exception;

/* Raised when Auth Service cannot provision a wallet for a new user. */
public class WalletProvisioningException extends RuntimeException {

	public WalletProvisioningException(String message, Throwable cause) {
		super(message, cause);
	}
}
