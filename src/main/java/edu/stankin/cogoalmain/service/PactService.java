package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Goal;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import edu.stankin.cogoalmain.db.entity.enums.InvitationStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.repo.PactInvitationRepository;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.db.repo.PactRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.service.event.DepositReleaseRequested;
import edu.stankin.cogoalmain.web.dto.pact.CreatePactRequest;
import edu.stankin.cogoalmain.web.dto.pact.JoinPactRequest;
import edu.stankin.cogoalmain.web.dto.pact.PactResponse;
import edu.stankin.cogoalmain.web.dto.pact.PactSummaryResponse;
import edu.stankin.cogoalmain.web.mapper.PactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pact lifecycle: OPEN (recruiting) → ACTIVE → FINISHED, or OPEN → CANCELLED.
 * <p>
 * Every state change first locks the pact row ({@link PactRepository#findForUpdate}) and only then loads
 * anything else of the pact, so concurrent joins, leaves, starts and cancels are serialized and all checks
 * see fresh data.
 */
@Service
@RequiredArgsConstructor
public class PactService {

    /** Deposit states in which no money is on its way; only then may a former participant rejoin. */
    private static final Set<DepositStatus> SETTLED_DEPOSIT =
            EnumSet.of(DepositStatus.NOT_STARTED, DepositStatus.FAILED, DepositStatus.RELEASED);

    private static final int MIN_ACTIVE_PARTICIPANTS_TO_START = 2;

    private final PactRepository pactRepository;
    private final PactParticipantRepository participantRepository;
    private final PactInvitationRepository invitationRepository;
    private final GoalService goalService;
    private final CharityService charityService;
    private final PactMapper pactMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Publishes the creator's DRAFT goal as a new OPEN pact; the creator becomes its first participant. */
    @Transactional
    public PactResponse create(UUID userId, CreatePactRequest request) {
        Goal goal = goalService.requireOwnDraft(userId, request.goalId());

        Pact pact = new Pact();
        pact.setGoal(goal);
        pact.setCharity(charityService.requireActive(request.charityId()));
        pact.setStatus(PactStatus.OPEN);
        pactRepository.save(pact);

        PactParticipant creator = new PactParticipant();
        creator.setPact(pact);
        creator.setUser(goal.getUser());
        enrollWithGoal(creator, goal, request.depositAmount());
        participantRepository.save(creator);

        return toResponse(pact);
    }

    @Transactional(readOnly = true)
    public Page<PactSummaryResponse> getMyPacts(UUID userId, Pageable pageable) {
        return participantRepository.findMine(userId, pageable).map(pactMapper::toSummary);
    }

    /** An OPEN pact is visible to everyone (to decide whether to join); later only to its participants. */
    @Transactional(readOnly = true)
    public PactResponse get(UUID userId, UUID pactId) {
        Pact pact = pactRepository.findDetailedById(pactId).orElseThrow(() -> NotFoundException.of("Pact", pactId));
        List<PactParticipant> participants = participantRepository.findAllInPact(pactId);

        boolean everParticipated = participants.stream().anyMatch(p -> p.getUser().getId().equals(userId));
        if (pact.getStatus() != PactStatus.OPEN && !everParticipated) {
            throw new ForbiddenException("Only participants can see a pact that is no longer open");
        }
        return pactMapper.toResponse(pact, withoutLeft(participants));
    }

    @Transactional
    public PactResponse join(UUID userId, UUID pactId, JoinPactRequest request) {
        Pact pact = lock(pactId);
        enroll(pact, userId, request);
        return toResponse(pact);
    }

    /** Leaving is possible only while the pact is recruiting; the creator cancels the pact instead. */
    @Transactional
    public void leave(UUID userId, UUID pactId) {
        Pact pact = lock(pactId);
        if (pact.getStatus() != PactStatus.OPEN) {
            throw new BusinessRuleException("A pact can only be left before it starts");
        }
        if (isCreator(pact, userId)) {
            throw new BusinessRuleException("The creator cannot leave the pact; cancel it instead");
        }
        PactParticipant participant = participantRepository.findByPactIdAndUserId(pactId, userId)
                .filter(p -> p.getStatus() != ParticipantStatus.LEFT)
                .orElseThrow(() -> new ForbiddenException("You are not a participant of this pact"));
        withdraw(participant);
    }

    /**
     * Starts the pact. Needs at least two participants whose deposit is held; everyone else is dropped
     * (their goals return to DRAFT) and pending invitations are cancelled.
     */
    @Transactional
    public PactResponse start(UUID userId, UUID pactId) {
        Pact pact = lock(pactId);
        requireCreator(pact, userId);
        requireOpen(pact);

        List<PactParticipant> participants = participantRepository.findAllInPact(pactId);
        long active = participants.stream().filter(p -> p.getStatus() == ParticipantStatus.ACTIVE).count();
        if (active < MIN_ACTIVE_PARTICIPANTS_TO_START) {
            throw new BusinessRuleException("At least " + MIN_ACTIVE_PARTICIPANTS_TO_START
                    + " participants with a paid deposit are needed to start (now: " + active + ")");
        }

        participants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.PENDING_DEPOSIT)
                .forEach(this::withdraw);
        pact.setStatus(PactStatus.ACTIVE);
        invitationRepository.cancelPending(pactId, clock.instant());

        return pactMapper.toResponse(pact, withoutLeft(participants));
    }

    /** Cancels a pact that has not started: everyone leaves, deposits are returned, goals become DRAFT. */
    @Transactional
    public void cancel(UUID userId, UUID pactId) {
        Pact pact = lock(pactId);
        requireCreator(pact, userId);
        if (pact.getStatus() != PactStatus.OPEN) {
            throw new BusinessRuleException("Only a pact that has not started can be cancelled");
        }

        participantRepository.findAllInPact(pactId).stream()
                .filter(p -> p.getStatus() != ParticipantStatus.LEFT)
                .forEach(this::withdraw);
        pact.setStatus(PactStatus.CANCELLED);
        invitationRepository.cancelPending(pactId, clock.instant());
    }

    /**
     * Adds the user to a pact the caller has already locked; used for joining and for accepting invitations.
     * A pending invitation of the user to this pact counts as accepted.
     */
    @Transactional
    public PactParticipant enroll(Pact lockedPact, UUID userId, JoinPactRequest request) {
        requireOpen(lockedPact);
        Goal goal = goalService.requireOwnDraft(userId, request.goalId());

        // (pact, user) is unique, so a former participant gets their old row back
        PactParticipant participant = participantRepository.findByPactIdAndUserId(lockedPact.getId(), userId)
                .map(this::requireRejoinable)
                .orElseGet(() -> {
                    PactParticipant fresh = new PactParticipant();
                    fresh.setPact(lockedPact);
                    fresh.setUser(goal.getUser());
                    return fresh;
                });
        enrollWithGoal(participant, goal, request.depositAmount());
        participantRepository.save(participant);

        invitationRepository.findByPactIdAndInviteeIdAndStatus(lockedPact.getId(), userId, InvitationStatus.PENDING)
                .ifPresent(invitation -> {
                    invitation.setStatus(InvitationStatus.ACCEPTED);
                    invitation.setRespondedAt(clock.instant());
                });
        return participant;
    }

    /**
     * The user's participation in the pact, for actions open to its members (chat, reports, proofs).
     *
     * @throws NotFoundException  if the pact does not exist
     * @throws ForbiddenException if the user is not a participant or has left
     */
    @Transactional(readOnly = true)
    public PactParticipant requireMember(UUID pactId, UUID userId) {
        return participantRepository.findByPactIdAndUserId(pactId, userId)
                .filter(p -> p.getStatus() != ParticipantStatus.LEFT)
                .orElseThrow(() -> pactRepository.existsById(pactId)
                        ? new ForbiddenException("Only participants of the pact can do this")
                        : NotFoundException.of("Pact", pactId));
    }

    /**
     * Locks the pact for a state change.
     *
     * @throws NotFoundException if it does not exist
     */
    @Transactional
    public Pact lock(UUID pactId) {
        return pactRepository.findForUpdate(pactId).orElseThrow(() -> NotFoundException.of("Pact", pactId));
    }

    PactResponse toResponse(Pact pact) {
        return pactMapper.toResponse(pact, withoutLeft(participantRepository.findAllInPact(pact.getId())));
    }

    private PactParticipant requireRejoinable(PactParticipant participant) {
        if (participant.getStatus() != ParticipantStatus.LEFT) {
            throw new BusinessRuleException("You are already a participant of this pact");
        }
        if (!SETTLED_DEPOSIT.contains(participant.getDepositStatus())) {
            throw new BusinessRuleException("Your previous deposit in this pact is still being processed ("
                    + participant.getDepositStatus() + "); try again later");
        }
        return participant;
    }

    private static void enrollWithGoal(PactParticipant participant, Goal goal, BigDecimal depositAmount) {
        participant.setGoal(goal);
        participant.setStatus(ParticipantStatus.PENDING_DEPOSIT);
        participant.setDepositAmount(depositAmount);
        participant.setDepositCurrency(PactParticipant.DEFAULT_CURRENCY);
        participant.setDepositStatus(DepositStatus.NOT_STARTED);
        participant.setReminderSentAt(null);
        goal.setStatus(GoalStatus.IN_PACT);
    }

    /** The participant leaves or is dropped: their goal returns to DRAFT and a held deposit is returned. */
    private void withdraw(PactParticipant participant) {
        participant.setStatus(ParticipantStatus.LEFT);
        participant.getGoal().setStatus(GoalStatus.DRAFT);
        if (participant.getDepositStatus() == DepositStatus.HELD) {
            events.publishEvent(new DepositReleaseRequested(participant.getId()));
        }
        // A deposit still PENDING is released when payment-service reports it as HELD
    }

    private static List<PactParticipant> withoutLeft(List<PactParticipant> participants) {
        return participants.stream().filter(p -> p.getStatus() != ParticipantStatus.LEFT).toList();
    }

    private static boolean isCreator(Pact pact, UUID userId) {
        return pact.getGoal().getUser().getId().equals(userId);
    }

    private static void requireCreator(Pact pact, UUID userId) {
        if (!isCreator(pact, userId)) {
            throw new ForbiddenException("Only the creator of the pact can do this");
        }
    }

    private static void requireOpen(Pact pact) {
        if (pact.getStatus() != PactStatus.OPEN) {
            throw new BusinessRuleException("The pact is not open for joining (status: " + pact.getStatus() + ")");
        }
    }
}
