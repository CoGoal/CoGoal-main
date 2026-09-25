package edu.stankin.cogoalmain.web.dto.goal;

import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Goal with its milestones, ordered by deadline.
 */
public record GoalResponse(
        UUID id,
        UUID ownerId,
        CategoryRef category,
        String title,
        String description,
        Instant deadline,
        GoalStatus status,
        List<MilestoneResponse> milestones,
        Instant createdAt,
        Instant updatedAt
) {
}
