package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.CheckIn;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.Review;
import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.repo.CheckInRepository;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.db.repo.ReviewRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.web.dto.checkin.CheckInResponse;
import edu.stankin.cogoalmain.web.dto.checkin.CreateReviewRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Partners review reports. The outcome is decided by {@link ReviewDecisionPolicy}.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final PactService pactService;
    private final CheckInService checkInService;
    private final CheckInRepository checkInRepository;
    private final ReviewRepository reviewRepository;
    private final PactParticipantRepository participantRepository;
    private final ReviewDecisionPolicy decisionPolicy;
    private final GoalOutcomeService goalOutcomeService;
    private final Clock clock;

    /**
     * Records the user's review of a partner's pending report and applies the decision:
     * an approved milestone report completes the milestone, an approved final report completes the goal.
     * Reports submitted in time may still be reviewed after the deadline.
     */
    @Transactional
    public CheckInResponse review(UUID userId, UUID checkInId, CreateReviewRequest request) {
        UUID pactId = checkInRepository.findPactIdById(checkInId)
                .orElseThrow(() -> NotFoundException.of("Check-in", checkInId));
        Pact pact = pactService.lock(pactId);
        CheckIn checkIn = checkInService.findDetailed(checkInId);

        PactParticipant reviewer = pactService.requireMember(pactId, userId);
        if (pact.getStatus() != PactStatus.ACTIVE || reviewer.getStatus() != ParticipantStatus.ACTIVE) {
            throw new ForbiddenException("Only active participants of an active pact can review");
        }
        if (checkIn.getParticipant().getId().equals(reviewer.getId())) {
            throw new ForbiddenException("You cannot review your own report");
        }
        if (checkIn.getStatus() != CheckInStatus.PENDING) {
            throw new BusinessRuleException("The report is already " + checkIn.getStatus());
        }
        if (reviewRepository.existsByCheckInIdAndReviewerId(checkInId, reviewer.getId())) {
            throw new BusinessRuleException("You have already reviewed this report");
        }
        requireStillRelevant(checkIn);

        Review review = new Review();
        review.setCheckIn(checkIn);
        review.setReviewer(reviewer);
        review.setStatus(request.status());
        review.setComment(request.comment());
        reviewRepository.save(review);

        List<Review> reviews = reviewRepository.findByCheckIn(checkInId);
        long eligibleReviewers = participantRepository.countByPactIdAndStatus(pactId, ParticipantStatus.ACTIVE) - 1;
        decisionPolicy.decide(reviews, eligibleReviewers).ifPresent(status -> apply(checkIn, status));

        return checkInService.toResponse(checkIn);
    }

    private void apply(CheckIn checkIn, CheckInStatus status) {
        checkIn.setStatus(status);
        if (status != CheckInStatus.APPROVED) {
            return;
        }
        Milestone milestone = checkIn.getMilestone();
        if (milestone != null) {
            milestone.setStatus(MilestoneStatus.COMPLETED);
            milestone.setCompletedAt(clock.instant());
        } else {
            // The final report was submitted before the deadline (checked on creation), so the goal is achieved
            goalOutcomeService.complete(checkIn.getParticipant());
        }
    }

    // The author may have failed meanwhile (missed deadline) — then there is nothing to approve any more
    private static void requireStillRelevant(CheckIn checkIn) {
        boolean authorActive = checkIn.getParticipant().getStatus() == ParticipantStatus.ACTIVE;
        boolean milestonePending = checkIn.getMilestone() == null
                || checkIn.getMilestone().getStatus() == MilestoneStatus.PENDING;
        if (!authorActive || !milestonePending) {
            throw new BusinessRuleException("The report can no longer be reviewed: its goal or milestone is closed");
        }
    }
}
