package edu.stankin.cogoalmain.web.dto.pact;

import edu.stankin.cogoalmain.db.entity.enums.PactStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param creatorId    owner of the pact's goal; only they can start or cancel the pact
 * @param participants everyone who has not left, in joining order
 */
public record PactResponse(
        UUID id,
        PactStatus status,
        UUID creatorId,
        UUID goalId,
        CharityRef charity,
        Instant createdAt,
        List<ParticipantResponse> participants
) {
}
