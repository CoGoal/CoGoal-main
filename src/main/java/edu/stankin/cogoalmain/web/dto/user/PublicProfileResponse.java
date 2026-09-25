package edu.stankin.cogoalmain.web.dto.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Profile as other users see it: no email and no coins.
 */
public record PublicProfileResponse(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String avatarUrl,
        UUID activeAvatarItemId,
        Instant createdAt
) {
}
