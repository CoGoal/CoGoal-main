package edu.stankin.cogoalmain.web.dto.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Profile of a newly registered user, sent by auth-service.
 *
 * @param id the user's id in auth-service, the same value as {@code sub} in the user's tokens
 */
public record InternalUserRequest(
        @NotNull UUID id,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 50) String username
) {
}
