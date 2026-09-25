package edu.stankin.cogoalmain.service.event;

import java.util.UUID;

/**
 * The participant missed a deadline: their held deposit goes to the pact's charity.
 */
public record DepositChargeRequested(UUID participantId, UUID charityId) {
}
