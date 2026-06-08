package com.gpay.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.auth_service.dto.LoginRequest;
import com.gpay.auth_service.dto.LoginResponse;
import com.gpay.auth_service.entity.RefreshToken;
import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.exception.UnauthorizedException;
import com.gpay.auth_service.repository.RefreshTokenRepository;
import com.gpay.auth_service.repository.UserRepository;
import com.gpay.auth_service.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthService} login behavior.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private JwtService jwtService;

	@Mock
	private PasswordEncoder passwordEncoder;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userRepository, refreshTokenRepository, jwtService, passwordEncoder, 7);
	}

	@Nested
	class Login {

		@Test
		void returnsTokensOnValidCredentials() {
			UUID userId = UUID.randomUUID();
			User user = User.create(userId, "user@example.com", "$2a$hashed", UserRole.USER, Instant.now(), Instant.now());

			when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
			when(passwordEncoder.matches("secret", "$2a$hashed")).thenReturn(true);
			when(jwtService.generateAccessToken(userId, "user@example.com")).thenReturn("access.token.jwt");
			when(passwordEncoder.encode(anyString())).thenReturn("$2a$refresh_hash");
			when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

			LoginResponse response = authService.login(new LoginRequest("user@example.com", "secret"));

			assertThat(response.accessToken()).isEqualTo("access.token.jwt");
			assertThat(response.refreshToken()).isNotBlank();
			verify(refreshTokenRepository).save(any(RefreshToken.class));
		}

		@Test
		void throwsUnauthorizedWhenEmailNotFound() {
			when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@example.com", "any")))
					.isInstanceOf(UnauthorizedException.class)
					.hasMessageContaining("Invalid credentials");

			verify(passwordEncoder, never()).matches(anyString(), anyString());
		}

		@Test
		void throwsUnauthorizedOnWrongPassword() {
			UUID userId = UUID.randomUUID();
			User user = User.create(userId, "user@example.com", "$2a$hashed", UserRole.USER, Instant.now(), Instant.now());

			when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
			when(passwordEncoder.matches("wrongpassword", "$2a$hashed")).thenReturn(false);

			assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrongpassword")))
					.isInstanceOf(UnauthorizedException.class)
					.hasMessageContaining("Invalid credentials");

			verify(refreshTokenRepository, never()).save(any());
		}

		@Test
		void refreshTokenIsStoredAsHash() {
			UUID userId = UUID.randomUUID();
			User user = User.create(userId, "user@example.com", "$2a$hashed", UserRole.USER, Instant.now(), Instant.now());

			when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
			when(passwordEncoder.matches("secret", "$2a$hashed")).thenReturn(true);
			when(jwtService.generateAccessToken(any(), any())).thenReturn("access.token.jwt");
			when(passwordEncoder.encode(anyString())).thenReturn("$2a$refresh_hash");
			when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

			LoginResponse response = authService.login(new LoginRequest("user@example.com", "secret"));

			// The raw refresh token returned must not be the hash
			assertThat(response.refreshToken()).isNotEqualTo("$2a$refresh_hash");
			// Password encoder encode was called to hash the raw token
			verify(passwordEncoder).encode(response.refreshToken());
		}
	}
}
