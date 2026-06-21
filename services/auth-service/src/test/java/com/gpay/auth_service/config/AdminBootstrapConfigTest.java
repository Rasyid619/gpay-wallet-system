package com.gpay.auth_service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.entity.UserRole;
import com.gpay.auth_service.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AdminBootstrapConfig} seeding and skip behavior.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapConfigTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Test
	void createsAdminUserWhenEmailAbsentAndPropertiesSet() {
		AdminBootstrapConfig config = configWith("admin@example.com", "AdminPassw0rd!");
		when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("AdminPassw0rd!")).thenReturn("hashed-secret");

		config.run(null);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		User saved = captor.getValue();
		assertThat(saved.getEmail()).isEqualTo("admin@example.com");
		assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(saved.getPasswordHash()).isEqualTo("hashed-secret");
	}

	@Test
	void skipsWhenUserAlreadyExists() {
		AdminBootstrapConfig config = configWith("admin@example.com", "AdminPassw0rd!");
		when(userRepository.findByEmail("admin@example.com"))
				.thenReturn(Optional.of(User.create(null, "admin@example.com", "hash", UserRole.ADMIN, null, null)));

		config.run(null);

		verify(userRepository, never()).save(any());
	}

	@Test
	void skipsWhenEmailBlank() {
		AdminBootstrapConfig config = configWith("  ", "AdminPassw0rd!");

		config.run(null);

		verify(userRepository, never()).findByEmail(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void skipsWhenPasswordBlank() {
		AdminBootstrapConfig config = configWith("admin@example.com", "");

		config.run(null);

		verify(userRepository, never()).findByEmail(any());
		verify(userRepository, never()).save(any());
	}

	private AdminBootstrapConfig configWith(String email, String password) {
		AdminBootstrapProperties properties = new AdminBootstrapProperties(email, password);
		return new AdminBootstrapConfig(userRepository, passwordEncoder, properties);
	}
}
