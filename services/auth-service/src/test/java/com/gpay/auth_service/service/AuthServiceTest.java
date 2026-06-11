package com.gpay.auth_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.auth_service.dto.LoginRequest;
import com.gpay.auth_service.dto.LoginResponse;
import com.gpay.auth_service.dto.RefreshRequest;
import com.gpay.auth_service.dto.RegisterRequest;
import com.gpay.auth_service.dto.RegisterResponse;
import com.gpay.auth_service.dto.UserMeResponse;
import com.gpay.auth_service.entity.RefreshToken;
import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.exception.ConflictException;
import com.gpay.auth_service.exception.NotFoundException;
import com.gpay.auth_service.exception.UnauthorizedException;
import com.gpay.auth_service.exception.WalletProvisioningException;
import com.gpay.auth_service.repository.RefreshTokenRepository;
import com.gpay.auth_service.repository.UserRepository;
import com.gpay.auth_service.security.HashUtil;
import com.gpay.auth_service.security.JwtService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AuthService} register, login, getMe, and refresh behavior.
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

	@Mock
	private WalletProvisioningClient walletProvisioningClient;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(
				userRepository,
				refreshTokenRepository,
				jwtService,
				passwordEncoder,
				walletProvisioningClient);
		ReflectionTestUtils.setField(authService, "refreshTokenExpirationDays", 7);
	}

	@Nested
	class Register {

		@Test
		void returnsCreatedUserOnValidRequest() {
			when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
			when(passwordEncoder.encode("Password1!")).thenReturn("$2a$hashed");
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			RegisterResponse response = authService.register(new RegisterRequest("new@example.com", "Password1!"));

			assertThat(response.email()).isEqualTo("new@example.com");
			assertThat(response.userId()).isNotNull();
			assertThat(response.createdAt()).isNotNull();
			verify(userRepository).save(any(User.class));
			verify(walletProvisioningClient).provisionWallet(response.userId(), null);
		}

		@Test
		void registersUserEvenWhenWalletProvisioningFails() {
			when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
			when(passwordEncoder.encode("Password1!")).thenReturn("$2a$hashed");
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
			doThrow(new WalletProvisioningException("Wallet Service unavailable", new RuntimeException()))
					.when(walletProvisioningClient).provisionWallet(any(), any());

			RegisterResponse response = authService.register(new RegisterRequest("new@example.com", "Password1!"));

			assertThat(response.email()).isEqualTo("new@example.com");
			assertThat(response.userId()).isNotNull();
			verify(userRepository).save(any(User.class));
			verify(walletProvisioningClient).provisionWallet(response.userId(), null);
		}

		@Test
		void throwsConflictOnDuplicateEmail() {
			UUID existingId = UUID.randomUUID();
			User existing = User.create(existingId, "taken@example.com", "$2a$hash", UserRole.USER, Instant.now(), Instant.now());
			when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

			assertThatThrownBy(() -> authService.register(new RegisterRequest("taken@example.com", "Password1!")))
					.isInstanceOf(ConflictException.class)
					.hasMessageContaining("already registered");

			verify(userRepository, never()).save(any());
			verify(walletProvisioningClient, never()).provisionWallet(any(), any());
		}

		@Test
		void passwordIsHashedBeforeSaving() {
			when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
			when(passwordEncoder.encode("Password1!")).thenReturn("$2a$hashed");
			when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

			authService.register(new RegisterRequest("new@example.com", "Password1!"));

			verify(passwordEncoder).encode("Password1!");
		}
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
		void refreshTokenIsStoredAsSha256Hash() {
			UUID userId = UUID.randomUUID();
			User user = User.create(userId, "user@example.com", "$2a$hashed", UserRole.USER, Instant.now(), Instant.now());

			when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
			when(passwordEncoder.matches("secret", "$2a$hashed")).thenReturn(true);
			when(jwtService.generateAccessToken(any(), any())).thenReturn("access.token.jwt");
			ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
			when(refreshTokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

			LoginResponse response = authService.login(new LoginRequest("user@example.com", "secret"));

			String storedHash = captor.getValue().getTokenHash();
			assertThat(storedHash).isEqualTo(HashUtil.sha256(response.refreshToken()));
			assertThat(storedHash).isNotEqualTo(response.refreshToken());
		}
	}

	@Nested
	class Refresh {

		private User user;
		private UUID userId;

		@BeforeEach
		void setUp() {
			userId = UUID.randomUUID();
			user = User.create(userId, "user@example.com", "$2a$hashed", UserRole.USER, Instant.now(), Instant.now());
		}

		@Test
		void returnsNewTokensOnValidRefreshToken() {
			String rawToken = "valid-raw-token";
			String tokenHash = HashUtil.sha256(rawToken);
			Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
			RefreshToken stored = RefreshToken.create(UUID.randomUUID(), user, tokenHash, expiresAt, Instant.now());

			when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
			when(jwtService.generateAccessToken(userId, "user@example.com")).thenReturn("new.access.token");
			when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

			LoginResponse response = authService.refresh(new RefreshRequest(rawToken));

			assertThat(response.accessToken()).isEqualTo("new.access.token");
			assertThat(response.refreshToken()).isNotBlank();
			assertThat(response.refreshToken()).isNotEqualTo(rawToken);
		}

		@Test
		void throwsUnauthorizedWhenTokenNotFound() {
			String rawToken = "unknown-token";
			when(refreshTokenRepository.findByTokenHash(HashUtil.sha256(rawToken))).thenReturn(Optional.empty());

			assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
					.isInstanceOf(UnauthorizedException.class)
					.hasMessageContaining("Invalid refresh token");
		}

		@Test
		void throwsUnauthorizedWhenTokenIsRevoked() {
			String rawToken = "revoked-token";
			String tokenHash = HashUtil.sha256(rawToken);
			RefreshToken stored = RefreshToken.create(UUID.randomUUID(), user, tokenHash,
					Instant.now().plus(7, ChronoUnit.DAYS), Instant.now());
			stored.revoke(Instant.now());

			when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));

			assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
					.isInstanceOf(UnauthorizedException.class)
					.hasMessageContaining("revoked");
		}

		@Test
		void throwsUnauthorizedWhenTokenIsExpired() {
			String rawToken = "expired-token";
			String tokenHash = HashUtil.sha256(rawToken);
			RefreshToken stored = RefreshToken.create(UUID.randomUUID(), user, tokenHash,
					Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());

			when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));

			assertThatThrownBy(() -> authService.refresh(new RefreshRequest(rawToken)))
					.isInstanceOf(UnauthorizedException.class)
					.hasMessageContaining("expired");
		}

		@Test
		void oldTokenIsRevokedOnRefresh() {
			String rawToken = "valid-raw-token";
			String tokenHash = HashUtil.sha256(rawToken);
			RefreshToken stored = RefreshToken.create(UUID.randomUUID(), user, tokenHash,
					Instant.now().plus(7, ChronoUnit.DAYS), Instant.now());

			when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
			when(jwtService.generateAccessToken(any(), any())).thenReturn("new.access.token");
			when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

			authService.refresh(new RefreshRequest(rawToken));

			assertThat(stored.isRevoked()).isTrue();
		}
	}

	@Nested
	class GetMe {

		@Test
		void returnsUserProfileForValidId() {
			UUID userId = UUID.randomUUID();
			User user = User.create(userId, "user@example.com", "$2a$hashed", UserRole.USER, Instant.now(), Instant.now());
			when(userRepository.findById(userId)).thenReturn(Optional.of(user));

			UserMeResponse response = authService.getMe(userId);

			assertThat(response.userId()).isEqualTo(userId);
			assertThat(response.email()).isEqualTo("user@example.com");
			assertThat(response.role()).isEqualTo("USER");
			assertThat(response.createdAt()).isNotNull();
		}

		@Test
		void throwsNotFoundWhenUserDoesNotExist() {
			UUID userId = UUID.randomUUID();
			when(userRepository.findById(userId)).thenReturn(Optional.empty());

			assertThatThrownBy(() -> authService.getMe(userId))
					.isInstanceOf(NotFoundException.class)
					.hasMessageContaining("User not found");
		}
	}
}
