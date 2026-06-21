package com.gpay.auth_service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gpay.auth_service.config.AdminBootstrapConfig;
import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.repository.UserRepository;
import com.gpay.auth_service.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/*
 * Integration test for the startup admin bootstrap seed against a real Flyway-migrated database.
 *
 * Uses a dedicated context (distinct property values) so the seed is enabled here only. The
 * runner is invoked after the shared per-test cleanup so the seeded row survives the assertions.
 */
@TestPropertySource(properties = {
	"admin.bootstrap.email=bootstrap-admin@example.com",
	"admin.bootstrap.password=AdminPassw0rd!"
})
class AdminBootstrapIntegrationTest extends AbstractIntegrationTest {

	private final AdminBootstrapConfig adminBootstrapConfig;
	private final UserRepository userRepository;
	private final JwtService jwtService;

	@Autowired
	AdminBootstrapIntegrationTest(
			AdminBootstrapConfig adminBootstrapConfig,
			UserRepository userRepository,
			JwtService jwtService) {
		this.adminBootstrapConfig = adminBootstrapConfig;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
	}

	@Test
	void seedsAdminUserWithAdminRoleAndTokenCarriesAdminRole() {
		adminBootstrapConfig.run(null);

		User admin = userRepository.findByEmail("bootstrap-admin@example.com").orElseThrow();
		assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);

		String accessToken = jwtService.generateAccessToken(admin.getId(), admin.getEmail(), admin.getRole());
		Claims claims = jwtService.parseToken(accessToken);
		assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
	}

	@Test
	void isIdempotentWhenInvokedTwice() {
		adminBootstrapConfig.run(null);
		adminBootstrapConfig.run(null);

		assertThat(userRepository.findByEmail("bootstrap-admin@example.com")).isPresent();
		assertThat(userRepository.count()).isEqualTo(1);
	}
}
