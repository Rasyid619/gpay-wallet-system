package com.gpay.auth_service.repository;

import com.gpay.auth_service.entity.RefreshToken;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/* Data access for the refresh_tokens table. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
}
