package edu.stankin.cogoalmain.web.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: {@code null} fields are left unchanged.
 */
public record UpdateProfileRequest(
        @Size(max = 50) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String username,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName
) {
}
