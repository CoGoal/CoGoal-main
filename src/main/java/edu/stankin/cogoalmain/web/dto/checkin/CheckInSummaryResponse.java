package edu.stankin.cogoalmain.web.dto.checkin;

import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.web.dto.goal.UserRef;

import java.time.Instant;
import java.util.UUID;

/**
 * @param milestone   the milestone reported on, or {@code null} for the final report on the whole goal
 * @param finalReport {@code true} when this reports the whole goal
 */
public record CheckInSummaryResponse(
        UUID id,
        UUID participantId,
        UserRef author,
        MilestoneRef milestone,
        boolean finalReport,
        CheckInStatus status,
        String comment,
        Instant submittedAt
) {
}
