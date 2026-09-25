package edu.stankin.cogoalmain.service.scheduler;

import edu.stankin.cogoalmain.client.payment.PaymentClient;
import edu.stankin.cogoalmain.config.AppProperties;
import edu.stankin.cogoalmain.service.DeadlineService;
import edu.stankin.cogoalmain.service.DeadlineService.UnsettledDeposit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Periodic jobs; intervals are configured under {@code app.deadlines}. Each item runs in its own transaction
 * and a failing item is logged and skipped, so the rest still get processed. Everything is idempotent,
 * so overlapping runs (or several application instances) are harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineScheduler {

    private final DeadlineService deadlineService;
    private final PaymentClient paymentClient;
    private final AppProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.deadlines.check-interval}")
    public void checkDeadlines() {
        Instant now = clock.instant();
        Instant remindUntil = now.plus(properties.deadlines().reminderBefore());

        forEach("milestone reminder", deadlineService.findMilestonesToRemind(now, remindUntil),
                id -> deadlineService.remindMilestone(id, now));
        forEach("goal reminder", deadlineService.findGoalsToRemind(now, remindUntil),
                id -> deadlineService.remindGoal(id, now));
        forEach("deadline decision", deadlineService.findParticipantsNeedingDecision(now),
                id -> deadlineService.decide(id, now));
    }

    /**
     * Sends charge and release requests again for deposits payment-service has not settled yet
     * (the first request is sent right after the decision; it may have been lost). payment-service
     * is expected to treat repeated requests for the same participant as one.
     */
    @Scheduled(fixedDelayString = "${app.deadlines.payment-retry-interval}",
            initialDelayString = "${app.deadlines.payment-retry-interval}")
    public void retryUnsettledDeposits() {
        List<UnsettledDeposit> deposits = deadlineService.findUnsettledDeposits();
        for (UnsettledDeposit deposit : deposits) {
            try {
                if (deposit.isCharge()) {
                    paymentClient.charge(deposit.participantId(), deposit.charityId());
                } else {
                    paymentClient.release(deposit.participantId());
                }
            } catch (RuntimeException e) {
                log.error("Retrying settlement of the deposit of participant {} failed", deposit.participantId(), e);
            }
        }
        if (!deposits.isEmpty()) {
            log.info("Re-sent settlement requests for {} deposit(s)", deposits.size());
        }
    }

    private static void forEach(String job, List<UUID> ids, Consumer<UUID> action) {
        for (UUID id : ids) {
            try {
                action.accept(id);
            } catch (RuntimeException e) {
                log.error("{} failed for {}", job, id, e);
            }
        }
    }
}
