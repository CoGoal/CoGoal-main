package edu.stankin.cogoalmain.web.dto.pact;

import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One of the current user's pacts, with their own participation in it.
 */
public record PactSummaryResponse(
        UUID id,
        PactStatus status,
        CharityRef charity,
        Instant createdAt,
        UUID participantId,
        ParticipantStatus myStatus,
        DepositStatus myDepositStatus
) {
}
