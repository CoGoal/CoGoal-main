package edu.stankin.cogoalmain.service.event;

import java.util.UUID;

/**
 * The participant's frozen deposit has to be returned (they left, the pact was cancelled,
 * or the deposit arrived after they were dropped).
 */
public record DepositReleaseRequested(UUID participantId) {
}
