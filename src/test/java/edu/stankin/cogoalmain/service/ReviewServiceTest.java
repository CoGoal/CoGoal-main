package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.CheckIn;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.Review;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.entity.enums.ReviewStatus;
import edu.stankin.cogoalmain.db.repo.CheckInRepository;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.db.repo.ReviewRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.web.dto.checkin.CreateReviewRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static edu.stankin.cogoalmain.support.PactFixtures.pact;
import static edu.stankin.cogoalmain.support.PactFixtures.participant;
import static edu.stankin.cogoalmain.support.PactFixtures.user;
import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Mock
    private PactService pactService;
    @Mock
    private CheckInService checkInService;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private PactParticipantRepository participantRepository;
    @Mock
    private GoalOutcomeService goalOutcomeService;

    private ReviewService service;

    private final User alice = user("alice");
    private final User bob = user("bob");
    private Pact pact;
    private PactParticipant author;
    private PactParticipant reviewer;
    private Milestone milestone;
    private CheckIn checkIn;
    private final List<Review> savedReviews = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ReviewService(pactService, checkInService, checkInRepository, reviewRepository,
                participantRepository, new ReviewDecisionPolicy(), goalOutcomeService, Clock.fixed(NOW, ZoneOffset.UTC));

        pact = pact(alice, PactStatus.ACTIVE);
        author = participant(pact, alice, ParticipantStatus.ACTIVE, DepositStatus.HELD);
        reviewer = participant(pact, bob, ParticipantStatus.ACTIVE, DepositStatus.HELD);

        milestone = withId(new Milestone());
        milestone.setGoal(author.getGoal());
        milestone.setStatus(MilestoneStatus.PENDING);

        checkIn = withId(new CheckIn());
        checkIn.setParticipant(author);
        checkIn.setMilestone(milestone);
        checkIn.setStatus(CheckInStatus.PENDING);

        given(checkInRepository.findPactIdById(checkIn.getId())).willReturn(Optional.of(pact.getId()));
        given(pactService.lock(pact.getId())).willReturn(pact);
        given(checkInService.findDetailed(checkIn.getId())).willReturn(checkIn);
        given(pactService.requireMember(pact.getId(), bob.getId())).willReturn(reviewer);
        given(pactService.requireMember(pact.getId(), alice.getId())).willReturn(author);
        given(participantRepository.countByPactIdAndStatus(pact.getId(), ParticipantStatus.ACTIVE)).willReturn(2L);
        given(reviewRepository.save(any())).willAnswer(invocation -> {
            savedReviews.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        given(reviewRepository.findByCheckIn(checkIn.getId())).willReturn(savedReviews);
    }

    @Test
    void approvalCompletesTheMilestone() {
        service.review(bob.getId(), checkIn.getId(), new CreateReviewRequest(ReviewStatus.APPROVED, null));

        assertThat(checkIn.getStatus()).isEqualTo(CheckInStatus.APPROVED);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.COMPLETED);
        assertThat(milestone.getCompletedAt()).isEqualTo(NOW);
        assertThat(savedReviews).singleElement().satisfies(r -> assertThat(r.getReviewer()).isSameAs(reviewer));
    }

    @Test
    void rejectionKeepsMilestoneOpenForANewReport() {
        service.review(bob.getId(), checkIn.getId(), new CreateReviewRequest(ReviewStatus.REJECTED, "No photo"));

        assertThat(checkIn.getStatus()).isEqualTo(CheckInStatus.REJECTED);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PENDING);
        assertThat(savedReviews.get(0).getComment()).isEqualTo("No photo");
        verifyNoInteractions(goalOutcomeService);
    }

    @Test
    void finalReportApprovalCompletesTheGoal() {
        checkIn.setMilestone(null);

        service.review(bob.getId(), checkIn.getId(), new CreateReviewRequest(ReviewStatus.APPROVED, null));

        assertThat(checkIn.getStatus()).isEqualTo(CheckInStatus.APPROVED);
        verify(goalOutcomeService).complete(author);
    }

    @Test
    void cannotReviewOwnReport() {
        assertThatThrownBy(() -> service.review(alice.getId(), checkIn.getId(),
                new CreateReviewRequest(ReviewStatus.APPROVED, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("own report");
        assertThat(checkIn.getStatus()).isEqualTo(CheckInStatus.PENDING);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void droppedOrFailedParticipantCannotReview() {
        reviewer.setStatus(ParticipantStatus.FAILED);

        assertThatThrownBy(() -> service.review(bob.getId(), checkIn.getId(),
                new CreateReviewRequest(ReviewStatus.APPROVED, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void eachPartnerReviewsOnce() {
        given(reviewRepository.existsByCheckInIdAndReviewerId(checkIn.getId(), reviewer.getId())).willReturn(true);

        assertThatThrownBy(() -> service.review(bob.getId(), checkIn.getId(),
                new CreateReviewRequest(ReviewStatus.APPROVED, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    void decidedReportCannotBeReviewedAgain() {
        checkIn.setStatus(CheckInStatus.APPROVED);

        assertThatThrownBy(() -> service.review(bob.getId(), checkIn.getId(),
                new CreateReviewRequest(ReviewStatus.REJECTED, "late")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reportOfFailedMilestoneCannotBeApproved() {
        milestone.setStatus(MilestoneStatus.FAILED);

        assertThatThrownBy(() -> service.review(bob.getId(), checkIn.getId(),
                new CreateReviewRequest(ReviewStatus.APPROVED, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer");
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.FAILED);
    }
}
