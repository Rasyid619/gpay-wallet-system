package com.gpay.auth_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/* Persisted application user credentials and role. */
@Getter
@Entity
@Table(name = "users")
public class User {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(nullable = false, unique = true, columnDefinition = "text")
	private String email;

	@Column(name = "password_hash", nullable = false, columnDefinition = "text")
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "user_role")
	private UserRole role;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	/**
	 * Creates a new user with all required fields.
	 *
	 * @param id           unique identifier
	 * @param email        unique email address
	 * @param passwordHash BCrypt password hash
	 * @param role         authorization role
	 * @param createdAt    creation timestamp
	 * @param updatedAt    last update timestamp
	 */
	public static User create(
			UUID id,
			String email,
			String passwordHash,
			UserRole role,
			Instant createdAt,
			Instant updatedAt) {
		User user = new User();
		user.id = id;
		user.email = email;
		user.passwordHash = passwordHash;
		user.role = role;
		user.createdAt = createdAt;
		user.updatedAt = updatedAt;
		return user;
	}
}
