package edu.stankin.cogoalmain.web.dto.checkin;

import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.web.dto.goal.UserRef;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Report with its evidence and reviews.
 *
 * @param milestone {@code null} for the final report on the whole goal
 */
public record CheckInResponse(
        UUID id,
        UUID pactId,
        UUID participantId,
        UserRef author,
        UUID goalId,
        MilestoneRef milestone,
        boolean finalReport,
        CheckInStatus status,
        String comment,
        Instant submittedAt,
        List<ProofResponse> proofs,
        List<ReviewResponse> reviews
) {
}
