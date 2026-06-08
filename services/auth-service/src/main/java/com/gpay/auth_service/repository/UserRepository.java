package com.gpay.auth_service.repository;

import com.gpay.auth_service.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/* Data access for the users table. */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);
}
