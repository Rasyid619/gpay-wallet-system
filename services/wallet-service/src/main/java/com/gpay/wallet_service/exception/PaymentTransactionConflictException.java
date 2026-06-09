package com.gpay.wallet_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/* Signals conflicting reuse of a payment transaction identifier. */
@ResponseStatus(HttpStatus.CONFLICT)
public class PaymentTransactionConflictException extends RuntimeException {

	public PaymentTransactionConflictException(String message) {
		super(message);
	}
}
