package edu.stankin.cogoalmain.web.dto.internal;

import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Outcome of a deposit operation, sent by payment-service.
 */
public record PaymentEventRequest(@NotNull UUID participantId, @NotNull Event event) {

    public enum Event {
        HELD(DepositStatus.HELD),
        CHARGED(DepositStatus.CHARGED),
        RELEASED(DepositStatus.RELEASED),
        FAILED(DepositStatus.FAILED);

        private final DepositStatus depositStatus;

        Event(DepositStatus depositStatus) {
            this.depositStatus = depositStatus;
        }

        public DepositStatus depositStatus() {
            return depositStatus;
        }
    }
}
