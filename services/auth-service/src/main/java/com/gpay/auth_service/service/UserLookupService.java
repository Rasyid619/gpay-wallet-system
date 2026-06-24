package com.gpay.auth_service.service;

import com.gpay.auth_service.dto.UserLookupResponse;
import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.exception.NotFoundException;
import com.gpay.auth_service.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Administrative read access to any user by identifier. */
@Service
@RequiredArgsConstructor
public class UserLookupService {

	private final UserRepository userRepository;

	/**
	 * Returns a non-sensitive view of any user for administrative lookups.
	 *
	 * @param userId user identifier
	 * @return narrow user lookup response without credential material
	 * @throws NotFoundException when no user exists for the identifier
	 */
	@Transactional(readOnly = true)
	public UserLookupResponse getUserById(UUID userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found"));

		return new UserLookupResponse(user.getId(), user.getEmail(), user.getRole().name(), user.getCreatedAt());
	}
}
