package edu.stankin.cogoalmain.service.event;

import edu.stankin.cogoalmain.client.email.EmailClient;
import edu.stankin.cogoalmain.client.payment.PaymentClient;
import edu.stankin.cogoalmain.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;

/**
 * Side effects outside the database (payment-service, email-service, file storage) run only after the state
 * change that caused them is committed, so a rolled-back transaction never releases money, sends an email
 * or deletes a file.
 * <p>
 * A failed call is logged and does not undo the committed change. Charge and release requests that got
 * no answer are sent again by {@code DeadlineScheduler#retryUnsettledDeposits}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalCallsListener {

    private final PaymentClient paymentClient;
    private final EmailClient emailClient;
    private final FileStorage fileStorage;

    @TransactionalEventListener
    public void on(DepositReleaseRequested event) {
        try {
            paymentClient.release(event.participantId());
        } catch (RuntimeException e) {
            log.error("Deposit release failed for participant {}", event.participantId(), e);
        }
    }

    @TransactionalEventListener
    public void on(DepositChargeRequested event) {
        try {
            paymentClient.charge(event.participantId(), event.charityId());
        } catch (RuntimeException e) {
            log.error("Deposit charge failed for participant {}", event.participantId(), e);
        }
    }

    @TransactionalEventListener
    public void on(PactResultNotification event) {
        try {
            emailClient.sendPactResult(event.email(), event.goalTitle(), event.result());
        } catch (RuntimeException e) {
            log.error("Pact result email to {} failed", event.email(), e);
        }
    }

    @TransactionalEventListener
    public void on(DeadlineReminderNotification event) {
        try {
            emailClient.sendDeadlineReminder(event.email(), event.goalTitle(), event.milestoneTitle(), event.deadline());
        } catch (RuntimeException e) {
            // Not retried: the reminder is marked as sent before the email goes out
            log.error("Deadline reminder email to {} failed", event.email(), e);
        }
    }

    @TransactionalEventListener
    public void on(ProofFileDeleted event) {
        try {
            fileStorage.delete(event.fileKey());
        } catch (IOException | RuntimeException e) {
            // Only an orphaned file is left behind; the proof itself is already gone
            log.warn("Could not delete proof file {}", event.fileKey(), e);
        }
    }

    @TransactionalEventListener
    public void on(InvitationCreated event) {
        try {
            emailClient.sendPactInvitation(event.inviteeEmail(), event.inviterUsername(),
                    event.pactId(), event.invitationId());
        } catch (RuntimeException e) {
            log.error("Invitation email failed for invitation {}", event.invitationId(), e);
        }
    }
}
