package edu.stankin.cogoalmain.web.dto.goal;

import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Goal in lists, without milestones.
 */
public record GoalSummaryResponse(
        UUID id,
        CategoryRef category,
        String title,
        Instant deadline,
        GoalStatus status,
        Instant createdAt
) {
}
