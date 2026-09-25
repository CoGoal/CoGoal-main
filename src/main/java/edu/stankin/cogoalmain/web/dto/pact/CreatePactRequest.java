package edu.stankin.cogoalmain.web.dto.pact;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param goalId        the creator's own DRAFT goal
 * @param charityId     active charity that receives deposits of participants who miss a deadline
 * @param depositAmount the creator's deposit, in RUB
 */
public record CreatePactRequest(
        @NotNull UUID goalId,
        @NotNull UUID charityId,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal depositAmount
) {
}
