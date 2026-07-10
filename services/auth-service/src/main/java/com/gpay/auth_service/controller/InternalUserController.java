package com.gpay.auth_service.controller;

import com.gpay.auth_service.dto.InternalUserLookupResponse;
import com.gpay.auth_service.service.InternalUserLookupService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Internal user endpoints for service-to-service workflows. */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

	private final InternalUserLookupService internalUserLookupService;

	@GetMapping("/{id}")
	public ResponseEntity<InternalUserLookupResponse> getUser(
			@RequestHeader("X-Internal-Token") String internalToken,
			@PathVariable UUID id) {
		return ResponseEntity.ok(internalUserLookupService.getUserById(internalToken, id));
	}
}
