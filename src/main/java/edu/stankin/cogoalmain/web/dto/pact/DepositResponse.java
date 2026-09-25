package edu.stankin.cogoalmain.web.dto.pact;

import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositResponse(
        UUID participantId,
        BigDecimal amount,
        String currency,
        DepositStatus depositStatus,
        ParticipantStatus participantStatus
) {
}
