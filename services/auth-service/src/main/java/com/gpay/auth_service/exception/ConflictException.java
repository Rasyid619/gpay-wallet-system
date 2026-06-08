package com.gpay.auth_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/* Signals that a resource already exists, for example a duplicate email. */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
