package edu.stankin.cogoalmain.web.dto.pact;

import edu.stankin.cogoalmain.web.dto.goal.UserRef;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(UUID id, UserRef sender, String text, Instant createdAt) {
}
