package edu.stankin.cogoalmain.web.dto.goal;

import edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus;

import java.time.Instant;
import java.util.UUID;

public record MilestoneResponse(
        UUID id,
        String title,
        String description,
        Instant deadline,
        MilestoneStatus status,
        Instant completedAt
) {
}
