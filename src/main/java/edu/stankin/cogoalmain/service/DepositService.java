package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.client.payment.PaymentClient;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ExternalServiceException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.service.event.DepositReleaseRequested;
import edu.stankin.cogoalmain.web.dto.internal.PaymentEventRequest;
import edu.stankin.cogoalmain.web.dto.pact.DepositResponse;
import edu.stankin.cogoalmain.web.dto.pact.DepositStartResponse;
import edu.stankin.cogoalmain.web.mapper.PactMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pact deposits: the participant starts the payment, payment-service reports the outcome.
 * <p>
 * Deposit states: NOT_STARTED → PENDING → HELD → CHARGED | RELEASED, with FAILED when the hold fails
 * (the participant may then try again). CHARGED and RELEASED are final.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositService {

    /** States from which the participant may (re)start paying; PENDING allows getting a new payment link. */
    private static final Set<DepositStatus> PAYABLE =
            EnumSet.of(DepositStatus.NOT_STARTED, DepositStatus.PENDING, DepositStatus.FAILED);

    private static final Set<DepositStatus> FINAL = EnumSet.of(DepositStatus.CHARGED, DepositStatus.RELEASED);

    private final PactService pactService;
    private final PactParticipantRepository participantRepository;
    private final PactMapper pactMapper;
    private final PaymentClient paymentClient;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;

    /**
     * Marks the deposit PENDING and asks payment-service for a payment page.
     * <p>
     * Not one transaction on purpose: the PENDING state is committed first and payment-service is called
     * afterwards, so a slow or failing payment call never holds the pact lock. If the call fails the
     * deposit stays PENDING and the participant can simply call this again.
     */
    public DepositStartResponse startDeposit(UUID userId, UUID pactId) {
        Hold hold = transactionTemplate.execute(status -> {
            Pact pact = pactService.lock(pactId);
            PactParticipant participant = findOwn(pactId, userId);

            if (pact.getStatus() != PactStatus.OPEN || participant.getStatus() != ParticipantStatus.PENDING_DEPOSIT) {
                throw new BusinessRuleException("A deposit can only be paid while waiting for it in an open pact");
            }
            if (!PAYABLE.contains(participant.getDepositStatus())) {
                throw new BusinessRuleException("The deposit is already " + participant.getDepositStatus());
            }
            participant.setDepositStatus(DepositStatus.PENDING);
            return new Hold(participant.getId(), userId, participant.getDepositAmount(),
                    participant.getDepositCurrency());
        });

        String paymentUrl;
        try {
            paymentUrl = paymentClient.holdDeposit(hold.participantId(), hold.userId(), hold.amount(), hold.currency());
        } catch (RuntimeException e) {
            log.error("Payment service failed to start a deposit for participant {}", hold.participantId(), e);
            throw new ExternalServiceException("Payment service is unavailable, please try again later");
        }
        return new DepositStartResponse(hold.participantId(), DepositStatus.PENDING, paymentUrl);
    }

    @Transactional(readOnly = true)
    public DepositResponse getMyDeposit(UUID userId, UUID pactId) {
        return pactMapper.toDeposit(findOwn(pactId, userId));
    }

    /**
     * Applies an outcome reported by payment-service. Idempotent: a repeated event changes nothing,
     * and events that would move a deposit out of a final state (e.g. a late HELD after RELEASED) are ignored.
     * <p>
     * HELD activates a participant who is still waiting in an open pact. If they have meanwhile left
     * or were dropped when the pact started, the money is returned right away.
     */
    @Transactional
    public DepositResponse handleEvent(PaymentEventRequest request) {
        UUID participantId = request.participantId();
        UUID pactId = participantRepository.findPactIdById(participantId)
                .orElseThrow(() -> NotFoundException.of("Participant", participantId));
        Pact pact = pactService.lock(pactId);
        PactParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> NotFoundException.of("Participant", participantId));

        DepositStatus current = participant.getDepositStatus();
        DepositStatus target = request.event().depositStatus();

        if (current == target) {
            return pactMapper.toDeposit(participant);
        }
        if (FINAL.contains(current) || !isAllowed(current, target)) {
            log.warn("Ignoring payment event {} for participant {} with deposit {}", target, participantId, current);
            return pactMapper.toDeposit(participant);
        }

        participant.setDepositStatus(target);
        if (target == DepositStatus.HELD) {
            onHeld(pact, participant);
        }
        return pactMapper.toDeposit(participant);
    }

    private void onHeld(Pact pact, PactParticipant participant) {
        if (pact.getStatus() == PactStatus.OPEN && participant.getStatus() == ParticipantStatus.PENDING_DEPOSIT) {
            participant.setStatus(ParticipantStatus.ACTIVE);
        } else if (participant.getStatus() == ParticipantStatus.LEFT) {
            events.publishEvent(new DepositReleaseRequested(participant.getId()));
        }
    }

    /** Which non-final deposit states an event may move the deposit out of. */
    private static boolean isAllowed(DepositStatus current, DepositStatus target) {
        return switch (target) {
            // FAILED → HELD: the user retried and the second attempt succeeded
            case HELD -> EnumSet.of(DepositStatus.NOT_STARTED, DepositStatus.PENDING, DepositStatus.FAILED)
                    .contains(current);
            // A failure report after the money is held is stale
            case FAILED -> EnumSet.of(DepositStatus.NOT_STARTED, DepositStatus.PENDING).contains(current);
            case CHARGED -> current == DepositStatus.HELD;
            // payment-service may also cancel a hold that is still pending
            case RELEASED -> true;
            // Set by this service only, never reported by payment-service
            case NOT_STARTED, PENDING -> false;
        };
    }

    private PactParticipant findOwn(UUID pactId, UUID userId) {
        return participantRepository.findByPactIdAndUserId(pactId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a participant of this pact"));
    }

    private record Hold(UUID participantId, UUID userId, BigDecimal amount, String currency) {
    }
}
