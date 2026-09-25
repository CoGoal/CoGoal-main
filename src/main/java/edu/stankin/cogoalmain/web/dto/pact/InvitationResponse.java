package edu.stankin.cogoalmain.web.dto.pact;

import edu.stankin.cogoalmain.db.entity.enums.InvitationStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.web.dto.goal.UserRef;

import java.time.Instant;
import java.util.UUID;

/**
 * @param goalTitle title of the pact creator's goal, to show what the invitation is about
 */
public record InvitationResponse(
        UUID id,
        UUID pactId,
        PactStatus pactStatus,
        String goalTitle,
        UserRef inviter,
        UserRef invitee,
        InvitationStatus status,
        Instant createdAt,
        Instant respondedAt
) {
}
