package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.CheckIn;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.repo.CheckInRepository;
import edu.stankin.cogoalmain.db.repo.MilestoneRepository;
import edu.stankin.cogoalmain.db.repo.ProofRepository;
import edu.stankin.cogoalmain.db.repo.ReviewRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.web.dto.checkin.CheckInResponse;
import edu.stankin.cogoalmain.web.dto.checkin.CreateCheckInRequest;
import edu.stankin.cogoalmain.web.mapper.CheckInMapperImpl;
import edu.stankin.cogoalmain.web.mapper.GoalMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static edu.stankin.cogoalmain.support.PactFixtures.goal;
import static edu.stankin.cogoalmain.support.PactFixtures.pact;
import static edu.stankin.cogoalmain.support.PactFixtures.participant;
import static edu.stankin.cogoalmain.support.PactFixtures.user;
import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckInServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Mock
    private PactService pactService;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private MilestoneRepository milestoneRepository;
    @Mock
    private ProofRepository proofRepository;
    @Mock
    private ReviewRepository reviewRepository;

    private CheckInService service;

    private final User alice = user("alice");
    private Pact pact;
    private PactParticipant author;
    private Milestone milestone;

    @BeforeEach
    void setUp() {
        service = new CheckInService(pactService, checkInRepository, milestoneRepository, proofRepository,
                reviewRepository, new CheckInMapperImpl(new GoalMapperImpl()), Clock.fixed(NOW, ZoneOffset.UTC));

        pact = pact(user("creator"), PactStatus.ACTIVE);
        author = participant(pact, alice, ParticipantStatus.ACTIVE, DepositStatus.HELD);
        author.getGoal().setDeadline(NOW.plus(Duration.ofDays(30)));
        milestone = milestone(NOW.plus(Duration.ofDays(10)));
        author.getGoal().getMilestones().add(milestone);

        given(pactService.lock(pact.getId())).willReturn(pact);
        given(pactService.requireMember(pact.getId(), alice.getId())).willReturn(author);
        given(milestoneRepository.findById(milestone.getId())).willReturn(Optional.of(milestone));
        given(checkInRepository.save(any())).willAnswer(invocation -> withId(invocation.<CheckIn>getArgument(0)));
    }

    @Nested
    class MilestoneReport {

        @Test
        void createsPendingReport() {
            CheckInResponse response = create(milestone.getId());

            assertThat(response.status()).isEqualTo(CheckInStatus.PENDING);
            assertThat(response.finalReport()).isFalse();
            assertThat(response.milestone().id()).isEqualTo(milestone.getId());
        }

        @Test
        void onlyActiveParticipantsOfActivePact() {
            pact.setStatus(PactStatus.OPEN);
            assertRejected(milestone.getId(), "active participants");

            pact.setStatus(PactStatus.ACTIVE);
            author.setStatus(ParticipantStatus.FAILED);
            assertRejected(milestone.getId(), "active participants");
        }

        @Test
        void onlyMilestonesOfOwnGoal() {
            Milestone foreign = milestone(NOW.plus(Duration.ofDays(5)));
            foreign.setGoal(goal(user("bob"), GoalStatus.IN_PACT));
            given(milestoneRepository.findById(foreign.getId())).willReturn(Optional.of(foreign));

            assertRejected(foreign.getId(), "not a milestone of your goal");
        }

        @Test
        void completedMilestoneCannotBeReportedAgain() {
            milestone.setStatus(MilestoneStatus.COMPLETED);

            assertRejected(milestone.getId(), "already COMPLETED");
        }

        @Test
        void notAfterTheDeadline() {
            milestone.setDeadline(NOW);

            assertRejected(milestone.getId(), "deadline");
        }

        @Test
        void noSecondReportWhileOneIsPendingOrApproved() {
            given(checkInRepository.existsByParticipantIdAndMilestoneIdAndStatusIn(
                    eq(author.getId()), eq(milestone.getId()), anyCollection())).willReturn(true);

            assertRejected(milestone.getId(), "already has a pending or approved report");
        }
    }

    @Nested
    class FinalReport {

        @Test
        void requiresAllMilestonesCompleted() {
            assertRejected(null, "All milestones must be completed");
        }

        @Test
        void allowedOnceMilestonesAreDone() {
            milestone.setStatus(MilestoneStatus.COMPLETED);

            CheckInResponse response = create(null);

            assertThat(response.finalReport()).isTrue();
            assertThat(response.milestone()).isNull();
        }

        @Test
        void onlyOnePendingOrApprovedFinalReport() {
            milestone.setStatus(MilestoneStatus.COMPLETED);
            given(checkInRepository.existsByParticipantIdAndMilestoneIsNullAndStatusIn(eq(author.getId()), anyCollection()))
                    .willReturn(true);

            assertRejected(null, "already has a pending or approved final report");
        }
    }

    private CheckInResponse create(java.util.UUID milestoneId) {
        return service.create(alice.getId(), pact.getId(), new CreateCheckInRequest(milestoneId, "Done"));
    }

    private void assertRejected(java.util.UUID milestoneId, String message) {
        assertThatThrownBy(() -> create(milestoneId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(message);
        verify(checkInRepository, never()).save(any());
    }

    private Milestone milestone(Instant deadline) {
        Milestone m = withId(new Milestone());
        m.setGoal(author.getGoal());
        m.setTitle("Step");
        m.setDeadline(deadline);
        m.setStatus(MilestoneStatus.PENDING);
        return m;
    }
}
