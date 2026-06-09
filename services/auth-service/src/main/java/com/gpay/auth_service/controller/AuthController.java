package com.gpay.auth_service.controller;

import com.gpay.auth_service.dto.LoginRequest;
import com.gpay.auth_service.dto.LoginResponse;
import com.gpay.auth_service.dto.RefreshRequest;
import com.gpay.auth_service.dto.RegisterRequest;
import com.gpay.auth_service.dto.RegisterResponse;
import com.gpay.auth_service.dto.UserMeResponse;
import com.gpay.auth_service.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* Auth endpoints for user identity and token management. */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@GetMapping("/me")
	public ResponseEntity<UserMeResponse> getMe(@AuthenticationPrincipal UUID userId) {
		return ResponseEntity.ok(authService.getMe(userId));
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request));
	}

	@PostMapping("/refresh")
	public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ResponseEntity.ok(authService.refresh(request));
	}
}
