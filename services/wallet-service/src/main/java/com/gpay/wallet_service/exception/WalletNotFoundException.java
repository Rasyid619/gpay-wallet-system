package com.gpay.wallet_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/* Signals that an authenticated user does not have a wallet row. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class WalletNotFoundException extends RuntimeException {

	public WalletNotFoundException(String message) {
		super(message);
	}
}
