package edu.stankin.cogoalmain.client.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Placeholder until payment-service exists: logs the call and returns a fake payment page URL.
 * Deposit status changes have to be simulated through {@code POST /internal/payments/events}.
 */
@Slf4j
@Component
public class StubPaymentClient implements PaymentClient {

    @Override
    public String holdDeposit(UUID participantId, UUID userId, BigDecimal amount, String currency) {
        log.info("[payment stub] hold deposit: participant={}, user={}, amount={} {}",
                participantId, userId, amount, currency);
        return "https://payment.stub.local/pay/" + participantId;
    }

    @Override
    public void charge(UUID participantId, UUID charityId) {
        log.info("[payment stub] charge deposit: participant={}, charity={}", participantId, charityId);
    }

    @Override
    public void release(UUID participantId) {
        log.info("[payment stub] release deposit: participant={}", participantId);
    }
}
