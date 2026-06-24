package com.gpay.auth_service.controller;

import com.gpay.auth_service.dto.UserLookupResponse;
import com.gpay.auth_service.service.UserLookupService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Admin endpoint for looking up any user by id, gated on ROLE_ADMIN. */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

	private final UserLookupService userLookupService;

	@GetMapping("/{id}")
	public ResponseEntity<UserLookupResponse> getUser(@PathVariable UUID id) {
		return ResponseEntity.ok(userLookupService.getUserById(id));
	}
}
