package com.gpay.auth_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for seeding an initial ADMIN user on startup.
 *
 * <p>Both values default to empty so the seed is disabled unless explicitly configured.
 *
 * @param email    admin email to provision; blank disables seeding
 * @param password raw admin password to hash; blank disables seeding
 */
@ConfigurationProperties(prefix = "admin.bootstrap")
public record AdminBootstrapProperties(String email, String password) {
}
