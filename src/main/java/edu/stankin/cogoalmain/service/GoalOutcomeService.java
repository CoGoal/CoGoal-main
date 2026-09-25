package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.config.AppProperties;
import edu.stankin.cogoalmain.db.entity.Goal;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.enums.CoinReason;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.service.event.DepositChargeRequested;
import edu.stankin.cogoalmain.service.event.DepositReleaseRequested;
import edu.stankin.cogoalmain.service.event.PactResultNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * The end of a participation in an active pact: the goal is either completed or failed.
 * <p>
 * Both are idempotent — they act only on an ACTIVE participant, so a repeated call (a second scheduler run,
 * a retried request) changes nothing, charges or releases nothing and credits no coins twice.
 * Callers must hold the pact lock ({@link PactService#lock}). Money moves and emails go out after commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalOutcomeService {

    private final PactParticipantRepository participantRepository;
    private final CoinService coinService;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;

    /**
     * Goal achieved (final report approved before the deadline): the deposit is returned and coins are credited.
     *
     * @return {@code false} if the participation was already over
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean complete(PactParticipant participant) {
        if (participant.getStatus() != ParticipantStatus.ACTIVE) {
            return false;
        }
        Goal goal = participant.getGoal();
        goal.setStatus(GoalStatus.COMPLETED);
        participant.setStatus(ParticipantStatus.COMPLETED);

        coinService.credit(participant.getUser().getId(), properties.coins().goalCompleted(),
                CoinReason.GOAL_COMPLETED, goal.getId());
        if (participant.getDepositStatus() == DepositStatus.HELD) {
            events.publishEvent(new DepositReleaseRequested(participant.getId()));
        }
        events.publishEvent(new PactResultNotification(participant.getUser().getEmail(), goal.getTitle(),
                ParticipantStatus.COMPLETED));
        log.info("Participant {} completed goal {}", participant.getId(), goal.getId());

        finishIfOver(participant.getPact());
        return true;
    }

    /**
     * A deadline was missed: the given milestones and the goal fail and the deposit goes to the pact's charity.
     *
     * @param overdue milestones whose deadline was missed (empty when the goal's own deadline was missed)
     * @return {@code false} if the participation was already over
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean fail(PactParticipant participant, Collection<Milestone> overdue) {
        if (participant.getStatus() != ParticipantStatus.ACTIVE) {
            return false;
        }
        overdue.forEach(milestone -> milestone.setStatus(MilestoneStatus.FAILED));
        Goal goal = participant.getGoal();
        goal.setStatus(GoalStatus.FAILED);
        participant.setStatus(ParticipantStatus.FAILED);

        Pact pact = participant.getPact();
        if (participant.getDepositStatus() == DepositStatus.HELD) {
            events.publishEvent(new DepositChargeRequested(participant.getId(), pact.getCharity().getId()));
        }
        events.publishEvent(new PactResultNotification(participant.getUser().getEmail(), goal.getTitle(),
                ParticipantStatus.FAILED));
        log.info("Participant {} failed goal {}", participant.getId(), goal.getId());

        finishIfOver(pact);
        return true;
    }

    /** The pact is finished once nobody is still pursuing their goal in it. */
    private void finishIfOver(Pact pact) {
        if (pact.getStatus() == PactStatus.ACTIVE
                && !participantRepository.existsByPactIdAndStatus(pact.getId(), ParticipantStatus.ACTIVE)) {
            pact.setStatus(PactStatus.FINISHED);
            log.info("Pact {} finished", pact.getId());
        }
    }
}
