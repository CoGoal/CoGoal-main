package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Goal;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.repo.CheckInRepository;
import edu.stankin.cogoalmain.db.repo.MilestoneRepository;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.service.event.DeadlineReminderNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Deadline decisions and reminders, one participation (or milestone) per transaction, so one broken record
 * does not stop the others and the pact lock is held only briefly. Driven by {@code DeadlineScheduler}.
 * <p>
 * Missed deadline: a milestone or goal whose deadline has passed fails unless it has an approved report.
 * A report that was submitted in time but is still waiting for review holds the decision until partners
 * decide; a rejection after the deadline then fails it on the next run (the author can no longer report).
 */
@Service
@RequiredArgsConstructor
public class DeadlineService {

    private static final Set<CheckInStatus> APPROVED = EnumSet.of(CheckInStatus.APPROVED);
    private static final Set<CheckInStatus> PENDING = EnumSet.of(CheckInStatus.PENDING);

    private final PactService pactService;
    private final PactParticipantRepository participantRepository;
    private final MilestoneRepository milestoneRepository;
    private final CheckInRepository checkInRepository;
    private final GoalOutcomeService outcomeService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<UUID> findParticipantsNeedingDecision(Instant now) {
        return participantRepository.findIdsNeedingDecision(now);
    }

    /**
     * Completes the goal if its final report is approved, fails it if a deadline was missed, otherwise
     * leaves it alone. Safe to run any number of times.
     */
    @Transactional
    public void decide(UUID participantId, Instant now) {
        UUID pactId = participantRepository.findPactIdById(participantId).orElse(null);
        if (pactId == null) {
            return;
        }
        Pact pact = pactService.lock(pactId);
        PactParticipant participant = participantRepository.findForProcessing(participantId).orElse(null);
        if (participant == null || participant.getStatus() != ParticipantStatus.ACTIVE
                || pact.getStatus() != PactStatus.ACTIVE) {
            return;
        }

        // Safety net: normally the review that approves the final report completes the goal right away
        if (checkInRepository.existsByParticipantIdAndMilestoneIsNullAndStatusIn(participantId, APPROVED)) {
            outcomeService.complete(participant);
            return;
        }

        Goal goal = participant.getGoal();
        List<Milestone> missed = goal.getMilestones().stream()
                .filter(m -> m.getStatus() == MilestoneStatus.PENDING)
                .filter(m -> !now.isBefore(deadlineOf(m, goal)))
                .filter(m -> !checkInRepository.existsByParticipantIdAndMilestoneIdAndStatusIn(
                        participantId, m.getId(), PENDING))
                .toList();
        if (!missed.isEmpty()) {
            outcomeService.fail(participant, missed);
            return;
        }

        boolean goalDeadlinePassed = !now.isBefore(goal.getDeadline());
        boolean finalReportAwaitingReview =
                checkInRepository.existsByParticipantIdAndMilestoneIsNullAndStatusIn(participantId, PENDING);
        if (goalDeadlinePassed && !finalReportAwaitingReview) {
            outcomeService.fail(participant, List.of());
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> findMilestonesToRemind(Instant now, Instant until) {
        return milestoneRepository.findIdsToRemind(now, until);
    }

    /** Sends the milestone reminder once: the conditional update lets only one run claim it. */
    @Transactional
    public void remindMilestone(UUID milestoneId, Instant now) {
        if (milestoneRepository.markReminderSent(milestoneId, now) == 0) {
            return;
        }
        milestoneRepository.findWithOwnerById(milestoneId).ifPresent(m -> events.publishEvent(
                new DeadlineReminderNotification(m.getGoal().getUser().getEmail(), m.getGoal().getTitle(),
                        m.getTitle(), m.getDeadline())));
    }

    @Transactional(readOnly = true)
    public List<UUID> findGoalsToRemind(Instant now, Instant until) {
        return participantRepository.findIdsToRemind(now, until);
    }

    /** Sends the reminder about the goal's final deadline once per participation. */
    @Transactional
    public void remindGoal(UUID participantId, Instant now) {
        if (participantRepository.markReminderSent(participantId, now) == 0) {
            return;
        }
        participantRepository.findForProcessing(participantId).ifPresent(p -> events.publishEvent(
                new DeadlineReminderNotification(p.getUser().getEmail(), p.getGoal().getTitle(), null,
                        p.getGoal().getDeadline())));
    }

    /** Deposits whose charge or release payment-service has not confirmed yet. */
    @Transactional(readOnly = true)
    public List<UnsettledDeposit> findUnsettledDeposits() {
        return participantRepository.findUnsettledDeposits().stream()
                .map(p -> p.getStatus() == ParticipantStatus.FAILED
                        ? UnsettledDeposit.charge(p.getId(), p.getPact().getCharity().getId())
                        : UnsettledDeposit.release(p.getId()))
                .toList();
    }

    private static Instant deadlineOf(Milestone milestone, Goal goal) {
        return milestone.getDeadline() != null ? milestone.getDeadline() : goal.getDeadline();
    }

    /**
     * @param charityId set when the deposit has to be charged, {@code null} when it has to be released
     */
    public record UnsettledDeposit(UUID participantId, UUID charityId) {

        static UnsettledDeposit charge(UUID participantId, UUID charityId) {
            return new UnsettledDeposit(participantId, charityId);
        }

        static UnsettledDeposit release(UUID participantId) {
            return new UnsettledDeposit(participantId, null);
        }

        public boolean isCharge() {
            return charityId != null;
        }
    }
}
