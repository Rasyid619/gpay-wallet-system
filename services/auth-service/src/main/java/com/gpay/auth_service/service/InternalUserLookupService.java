package com.gpay.auth_service.service;

import com.gpay.auth_service.dto.InternalUserLookupResponse;
import com.gpay.auth_service.entity.User;
import com.gpay.auth_service.exception.NotFoundException;
import com.gpay.auth_service.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/* Internal-token-gated user lookup for service-to-service workflows. */
@Service
@RequiredArgsConstructor
public class InternalUserLookupService {

	private final InternalUserAuthenticationService internalUserAuthenticationService;
	private final UserRepository userRepository;

	/**
	 * Returns the id and email of a user for a trusted internal caller.
	 *
	 * @param providedInternalToken internal service token from the request header
	 * @param userId                user identifier
	 * @return narrow user view without credential material
	 * @throws NotFoundException when no user exists for the identifier
	 */
	@Transactional(readOnly = true)
	public InternalUserLookupResponse getUserById(String providedInternalToken, UUID userId) {
		internalUserAuthenticationService.validate(providedInternalToken);

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("User not found"));
		return new InternalUserLookupResponse(user.getId(), user.getEmail());
	}
}
