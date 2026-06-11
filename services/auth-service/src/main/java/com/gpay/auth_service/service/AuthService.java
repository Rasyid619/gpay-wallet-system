package com.gpay.auth_service.service;

import com.gpay.auth_service.config.TraceIdContext;
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
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Handles authentication business logic. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtService jwtService;
	private final PasswordEncoder passwordEncoder;
	private final WalletProvisioningClient walletProvisioningClient;
	@Value("${jwt.refresh-token-expiration-days}")
	private int refreshTokenExpirationDays;

	/**
	 * Returns the profile of the authenticated user.
	 *
	 * @param userId authenticated user's identifier from the JWT subject
	 * @return user profile without sensitive fields
	 * @throws NotFoundException if the user no longer exists
	 */
	@Transactional(readOnly = true)
	public UserMeResponse getMe(UUID userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found"));
		return new UserMeResponse(user.getId(), user.getEmail(), user.getRole().name(), user.getCreatedAt());
	}

	/**
	 * Registers a new user account.
	 *
	 * <p>Wallet provisioning is best-effort: registration succeeds even when Wallet
	 * Service is unavailable, and the wallet is provisioned on first wallet access.
	 *
	 * @param request registration details
	 * @return created user summary
	 * @throws ConflictException if email is already registered
	 */
	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		if (userRepository.findByEmail(request.email()).isPresent()) {
			throw new ConflictException("Email is already registered");
		}

		Instant now = Instant.now();
		String passwordHash = passwordEncoder.encode(request.password());
		User user = User.create(UUID.randomUUID(), request.email(), passwordHash, UserRole.USER, now, now);
		userRepository.save(user);
		provisionWalletBestEffort(user.getId(), TraceIdContext.getTraceId());

		return new RegisterResponse(user.getId(), user.getEmail(), user.getCreatedAt());
	}

	private void provisionWalletBestEffort(UUID userId, String traceId) {
		try {
			walletProvisioningClient.provisionWallet(userId, traceId);
		} catch (WalletProvisioningException ex) {
			log.warn(
					"Wallet provisioning failed during registration; wallet will be provisioned on first access. "
							+ "userId={} traceId={}",
					userId,
					traceId,
					ex);
		}
	}

	/**
	 * Authenticates a user and returns JWT access and refresh tokens.
	 *
	 * @param request login credentials
	 * @return access token and refresh token pair
	 * @throws UnauthorizedException if email is not found or password does not match
	 */
	@Transactional
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new UnauthorizedException("Invalid credentials");
		}

		String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
		String rawRefreshToken = generateRawRefreshToken();
		String tokenHash = HashUtil.sha256(rawRefreshToken);

		Instant now = Instant.now();
		Instant expiresAt = now.plus(refreshTokenExpirationDays, ChronoUnit.DAYS);
		RefreshToken refreshTokenEntity = RefreshToken.create(UUID.randomUUID(), user, tokenHash, expiresAt, now);
		refreshTokenRepository.save(refreshTokenEntity);

		return new LoginResponse(accessToken, rawRefreshToken);
	}

	private String generateRawRefreshToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * Validates the submitted refresh token and issues a new token pair (rotation).
	 *
	 * <p>The old refresh token is revoked and a new one is issued atomically.
	 *
	 * @param request refresh token payload
	 * @return new access token and refresh token pair
	 * @throws UnauthorizedException if the token is unknown, expired, or revoked
	 */
	@Transactional
	public LoginResponse refresh(RefreshRequest request) {
		String tokenHash = HashUtil.sha256(request.refreshToken());

		RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

		if (stored.isRevoked()) {
			throw new UnauthorizedException("Refresh token has been revoked");
		}
		if (stored.isExpired()) {
			throw new UnauthorizedException("Refresh token has expired");
		}

		// Revoke old token (rotation)
		stored.revoke(Instant.now());
		refreshTokenRepository.save(stored);

		// Issue new token pair
		User user = stored.getUser();
		String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
		String newRawRefreshToken = generateRawRefreshToken();
		String newTokenHash = HashUtil.sha256(newRawRefreshToken);

		Instant now = Instant.now();
		Instant expiresAt = now.plus(refreshTokenExpirationDays, ChronoUnit.DAYS);
		RefreshToken newToken = RefreshToken.create(UUID.randomUUID(), user, newTokenHash, expiresAt, now);
		refreshTokenRepository.save(newToken);

		return new LoginResponse(newAccessToken, newRawRefreshToken);
	}
}
