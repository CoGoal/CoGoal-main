package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.CheckIn;
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
import edu.stankin.cogoalmain.db.repo.ProofRepository;
import edu.stankin.cogoalmain.db.repo.ReviewRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.web.dto.checkin.CheckInResponse;
import edu.stankin.cogoalmain.web.dto.checkin.CheckInSummaryResponse;
import edu.stankin.cogoalmain.web.dto.checkin.CreateCheckInRequest;
import edu.stankin.cogoalmain.web.mapper.CheckInMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reports (check-ins) on milestones and on whole goals.
 * <p>
 * Only an active participant of an active pact reports, only on their own goal, before the deadline.
 * A milestone (or the goal) can have at most one report that is pending or approved; after a rejection
 * the author may report again.
 */
@Service
@RequiredArgsConstructor
public class CheckInService {

    private static final Set<CheckInStatus> BLOCKING = EnumSet.of(CheckInStatus.PENDING, CheckInStatus.APPROVED);

    private final PactService pactService;
    private final CheckInRepository checkInRepository;
    private final MilestoneRepository milestoneRepository;
    private final ProofRepository proofRepository;
    private final ReviewRepository reviewRepository;
    private final CheckInMapper checkInMapper;
    private final Clock clock;

    @Transactional
    public CheckInResponse create(UUID userId, UUID pactId, CreateCheckInRequest request) {
        Pact pact = pactService.lock(pactId);
        PactParticipant author = pactService.requireMember(pactId, userId);
        if (pact.getStatus() != PactStatus.ACTIVE || author.getStatus() != ParticipantStatus.ACTIVE) {
            throw new BusinessRuleException("Reports can only be submitted by active participants of an active pact");
        }

        Milestone milestone = request.milestoneId() == null
                ? requireFinalReportAllowed(author)
                : requireMilestoneReportAllowed(author, request.milestoneId());

        CheckIn checkIn = new CheckIn();
        checkIn.setParticipant(author);
        checkIn.setMilestone(milestone);
        checkIn.setStatus(CheckInStatus.PENDING);
        checkIn.setComment(request.comment());
        return checkInMapper.toResponse(checkInRepository.save(checkIn), List.of(), List.of());
    }

    /** Reports of the pact, visible to its participants. */
    @Transactional(readOnly = true)
    public Page<CheckInSummaryResponse> listForPact(UUID userId, UUID pactId, Pageable pageable) {
        pactService.requireMember(pactId, userId);
        return checkInRepository.findByPact(pactId, pageable).map(checkInMapper::toSummary);
    }

    /** Report with proofs and reviews, visible to participants of its pact. */
    @Transactional(readOnly = true)
    public CheckInResponse get(UUID userId, UUID checkInId) {
        CheckIn checkIn = findDetailed(checkInId);
        pactService.requireMember(checkIn.getParticipant().getPact().getId(), userId);
        return toResponse(checkIn);
    }

    /** Pending reports of partners the user has not reviewed yet. */
    @Transactional(readOnly = true)
    public Page<CheckInSummaryResponse> toReview(UUID userId, Pageable pageable) {
        return checkInRepository.findToReview(userId, pageable).map(checkInMapper::toSummary);
    }

    CheckIn findDetailed(UUID checkInId) {
        return checkInRepository.findDetailedById(checkInId)
                .orElseThrow(() -> NotFoundException.of("Check-in", checkInId));
    }

    CheckInResponse toResponse(CheckIn checkIn) {
        return checkInMapper.toResponse(checkIn,
                proofRepository.findByCheckInIdOrderByUploadedAt(checkIn.getId()),
                reviewRepository.findByCheckIn(checkIn.getId()));
    }

    private Milestone requireMilestoneReportAllowed(PactParticipant author, UUID milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> NotFoundException.of("Milestone", milestoneId));
        Goal goal = author.getGoal();

        if (!milestone.getGoal().getId().equals(goal.getId())) {
            throw new BusinessRuleException("This is not a milestone of your goal in this pact");
        }
        if (milestone.getStatus() != MilestoneStatus.PENDING) {
            throw new BusinessRuleException("The milestone is already " + milestone.getStatus());
        }
        requireBeforeDeadline(milestone.getDeadline() != null ? milestone.getDeadline() : goal.getDeadline());
        if (checkInRepository.existsByParticipantIdAndMilestoneIdAndStatusIn(author.getId(), milestoneId, BLOCKING)) {
            throw new BusinessRuleException("This milestone already has a pending or approved report");
        }
        return milestone;
    }

    /** The final report closes the goal, so every milestone has to be completed first. */
    private Milestone requireFinalReportAllowed(PactParticipant author) {
        Goal goal = author.getGoal();

        boolean allMilestonesDone = goal.getMilestones().stream()
                .allMatch(m -> m.getStatus() == MilestoneStatus.COMPLETED);
        if (!allMilestonesDone) {
            throw new BusinessRuleException("All milestones must be completed before the final report");
        }
        requireBeforeDeadline(goal.getDeadline());
        if (checkInRepository.existsByParticipantIdAndMilestoneIsNullAndStatusIn(author.getId(), BLOCKING)) {
            throw new BusinessRuleException("The goal already has a pending or approved final report");
        }
        return null;
    }

    private void requireBeforeDeadline(Instant deadline) {
        if (!clock.instant().isBefore(deadline)) {
            throw new BusinessRuleException("The deadline (" + deadline + ") has passed");
        }
    }
}
