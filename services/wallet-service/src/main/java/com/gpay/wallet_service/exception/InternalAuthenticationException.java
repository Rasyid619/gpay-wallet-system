package com.gpay.wallet_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/* Signals invalid internal service-to-service authentication. */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InternalAuthenticationException extends RuntimeException {

	public InternalAuthenticationException(String message) {
		super(message);
	}
}
