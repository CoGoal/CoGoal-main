package edu.stankin.cogoalmain.web.dto.pact;

import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.web.dto.goal.GoalSummaryResponse;
import edu.stankin.cogoalmain.web.dto.goal.UserRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ParticipantResponse(
        UUID id,
        UserRef user,
        GoalSummaryResponse goal,
        ParticipantStatus status,
        BigDecimal depositAmount,
        String depositCurrency,
        DepositStatus depositStatus,
        Instant joinedAt
) {
}
