package edu.stankin.cogoalmain.web.dto.user;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param shopItemId an AVATAR item the user has purchased
 */
public record SetAvatarRequest(@NotNull UUID shopItemId) {
}
