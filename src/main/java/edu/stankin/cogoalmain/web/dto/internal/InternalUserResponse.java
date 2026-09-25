package edu.stankin.cogoalmain.web.dto.internal;

import java.util.UUID;

public record InternalUserResponse(UUID id, String email, String username) {
}
