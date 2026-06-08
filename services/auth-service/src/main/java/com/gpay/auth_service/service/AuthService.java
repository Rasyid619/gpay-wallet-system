package com.gpay.auth_service.service;

import com.gpay.auth_service.dto.LoginRequest;
import com.gpay.auth_service.dto.LoginResponse;
import com.gpay.auth_service.dto.RegisterRequest;
import com.gpay.auth_service.dto.RegisterResponse;
import com.gpay.auth_service.entity.RefreshToken;
import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.exception.ConflictException;
import com.gpay.auth_service.exception.UnauthorizedException;
import com.gpay.auth_service.repository.RefreshTokenRepository;
import com.gpay.auth_service.repository.UserRepository;
import com.gpay.auth_service.security.JwtService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Handles authentication business logic. */
@Service
public class AuthService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final JwtService jwtService;
	private final PasswordEncoder passwordEncoder;
	private final int refreshTokenExpirationDays;

	public AuthService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			JwtService jwtService,
			PasswordEncoder passwordEncoder,
			@Value("${jwt.refresh-token-expiration-days}") int refreshTokenExpirationDays) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.jwtService = jwtService;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenExpirationDays = refreshTokenExpirationDays;
	}

	/**
	 * Registers a new user account.
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

		return new RegisterResponse(user.getId(), user.getEmail(), user.getCreatedAt());
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
		String tokenHash = passwordEncoder.encode(rawRefreshToken);

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
}
