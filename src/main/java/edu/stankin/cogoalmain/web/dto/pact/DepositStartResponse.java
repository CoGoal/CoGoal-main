package edu.stankin.cogoalmain.web.dto.pact;

import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;

import java.util.UUID;

/**
 * @param paymentUrl page where the user confirms the payment; the result arrives later from payment-service
 */
public record DepositStartResponse(UUID participantId, DepositStatus depositStatus, String paymentUrl) {
}
