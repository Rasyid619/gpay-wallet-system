package com.gpay.auth_service.config;

import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/* Provisions an initial ADMIN user from environment config on startup. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapConfig implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AdminBootstrapProperties properties;

	/**
	 * Seeds the bootstrap ADMIN user when configured and absent.
	 *
	 * <p>Runs after Flyway and full context initialization so the schema exists. The seed is
	 * skipped silently when either credential is blank or the email is already registered.
	 */
	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		String email = properties.email();
		String password = properties.password();

		if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
			log.info("Admin bootstrap disabled: ADMIN_BOOTSTRAP_EMAIL or ADMIN_BOOTSTRAP_PASSWORD is unset");
			return;
		}

		if (userRepository.findByEmail(email).isPresent()) {
			log.info("Admin bootstrap skipped: user already exists for email={}", email);
			return;
		}

		Instant now = Instant.now();
		String passwordHash = passwordEncoder.encode(password);
		User admin = User.create(UUID.randomUUID(), email, passwordHash, UserRole.ADMIN, now, now);
		userRepository.save(admin);
		log.info("Admin bootstrap created ADMIN user for email={}", email);
	}
}
