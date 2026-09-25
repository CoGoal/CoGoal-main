package edu.stankin.cogoalmain.web.dto.user;

import edu.stankin.cogoalmain.web.dto.shop.ShopItemSummary;

import java.time.Instant;
import java.util.UUID;

/**
 * The current user's own profile, including private fields (email, coins).
 *
 * @param activeAvatar purchased AVATAR item shown on the profile, or {@code null}
 */
public record MyProfileResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String avatarUrl,
        ShopItemSummary activeAvatar,
        int coins,
        Instant createdAt
) {
}
