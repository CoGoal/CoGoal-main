package edu.stankin.cogoalmain.client.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * payment-service: freezes, charges and returns pact deposits.
 * <p>
 * All calls are asynchronous from main's point of view: the outcome arrives later
 * as an event on {@code POST /internal/payments/events}.
 */
public interface PaymentClient {

    /**
     * Starts freezing the participant's deposit.
     *
     * @return URL of the payment page the user has to open to confirm the payment
     */
    String holdDeposit(UUID participantId, UUID userId, BigDecimal amount, String currency);

    /** Charges the frozen deposit to the pact's charity (the participant missed a deadline). */
    void charge(UUID participantId, UUID charityId);

    /** Returns the frozen deposit to the participant. */
    void release(UUID participantId);
}
