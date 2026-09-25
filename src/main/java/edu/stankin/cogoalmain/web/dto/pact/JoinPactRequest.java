package edu.stankin.cogoalmain.web.dto.pact;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Joining a pact directly or by accepting an invitation.
 *
 * @param goalId        the user's own DRAFT goal to pursue in the pact
 * @param depositAmount the user's deposit, in RUB
 */
public record JoinPactRequest(
        @NotNull UUID goalId,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal depositAmount
) {
}
