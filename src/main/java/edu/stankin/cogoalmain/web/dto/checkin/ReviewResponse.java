package edu.stankin.cogoalmain.web.dto.checkin;

import edu.stankin.cogoalmain.db.entity.enums.ReviewStatus;
import edu.stankin.cogoalmain.web.dto.goal.UserRef;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(UUID id, UserRef reviewer, ReviewStatus status, String comment, Instant createdAt) {
}
