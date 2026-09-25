package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.config.AppProperties;
import edu.stankin.cogoalmain.db.entity.Milestone;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.CheckInStatus;
import edu.stankin.cogoalmain.db.entity.enums.CoinReason;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import edu.stankin.cogoalmain.db.entity.enums.MilestoneStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.repo.CheckInRepository;
import edu.stankin.cogoalmain.db.repo.MilestoneRepository;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.service.event.DeadlineReminderNotification;
import edu.stankin.cogoalmain.service.event.DepositChargeRequested;
import edu.stankin.cogoalmain.service.event.DepositReleaseRequested;
import edu.stankin.cogoalmain.service.event.PactResultNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static edu.stankin.cogoalmain.support.PactFixtures.pact;
import static edu.stankin.cogoalmain.support.PactFixtures.participant;
import static edu.stankin.cogoalmain.support.PactFixtures.user;
import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Deadline decisions end to end through the real {@link GoalOutcomeService}; only repositories,
 * coins and the event publisher are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeadlineServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final int COINS = 100;

    @Mock
    private PactService pactService;
    @Mock
    private PactParticipantRepository participantRepository;
    @Mock
    private MilestoneRepository milestoneRepository;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private CoinService coinService;
    @Mock
    private ApplicationEventPublisher events;

    private DeadlineService service;

    private final User alice = user("alice");
    private Pact pact;
    private PactParticipant participant;
    private Milestone milestone;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(new AppProperties.Coins(COINS),
                new AppProperties.Deadlines(false, Duration.ofMinutes(5), Duration.ofHours(24), Duration.ofHours(1)),
                new AppProperties.Storage(Path.of("x"), DataSize.ofMegabytes(1)),
                new AppProperties.Internal("test-internal-token-123456"));
        GoalOutcomeService outcomeService = new GoalOutcomeService(participantRepository, coinService, properties, events);
        service = new DeadlineService(pactService, participantRepository, milestoneRepository, checkInRepository,
                outcomeService, events);

        pact = pact(user("creator"), PactStatus.ACTIVE);
        participant = participant(pact, alice, ParticipantStatus.ACTIVE, DepositStatus.HELD);
        participant.getGoal().setDeadline(NOW.plus(Duration.ofDays(10)));
        milestone = withId(new Milestone());
        milestone.setGoal(participant.getGoal());
        milestone.setTitle("First step");
        milestone.setStatus(MilestoneStatus.PENDING);
        milestone.setDeadline(NOW.minus(Duration.ofHours(1)));
        participant.getGoal().getMilestones().add(milestone);

        given(participantRepository.findPactIdById(participant.getId())).willReturn(Optional.of(pact.getId()));
        given(pactService.lock(pact.getId())).willReturn(pact);
        given(participantRepository.findForProcessing(participant.getId())).willReturn(Optional.of(participant));
        // Other participants are still pursuing their goals unless a test says otherwise
        given(participantRepository.existsByPactIdAndStatus(pact.getId(), ParticipantStatus.ACTIVE)).willReturn(true);
    }

    @Nested
    class MissedMilestone {

        @Test
        void withoutReportFailsGoalAndChargesDepositToCharity() {
            service.decide(participant.getId(), NOW);

            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.FAILED);
            assertThat(participant.getGoal().getStatus()).isEqualTo(GoalStatus.FAILED);
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.FAILED);
            verify(events).publishEvent(new DepositChargeRequested(participant.getId(), pact.getCharity().getId()));
            verify(events).publishEvent(new PactResultNotification("alice@example.com",
                    participant.getGoal().getTitle(), ParticipantStatus.FAILED));
        }

        @Test
        void reportWaitingForReviewPostponesTheDecision() {
            givenReport(milestone.getId(), CheckInStatus.PENDING);

            service.decide(participant.getId(), NOW);

            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PENDING);
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
            verifyNoInteractions(events);
        }

        @Test
        void reportRejectedAfterTheDeadlineFails() {
            // Rejected report is neither pending nor approved, and the author can no longer report again
            service.decide(participant.getId(), NOW);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.FAILED);
        }

        @Test
        void milestoneBeforeItsDeadlineIsLeftAlone() {
            milestone.setDeadline(NOW.plusSeconds(1));

            service.decide(participant.getId(), NOW);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
            verifyNoInteractions(events);
        }
    }

    @Nested
    class GoalDeadline {

        @BeforeEach
        void allMilestonesDone() {
            milestone.setStatus(MilestoneStatus.COMPLETED);
            participant.getGoal().setDeadline(NOW.minusSeconds(1));
        }

        @Test
        void passedWithoutFinalReportFails() {
            service.decide(participant.getId(), NOW);

            assertThat(participant.getGoal().getStatus()).isEqualTo(GoalStatus.FAILED);
            assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.COMPLETED);
            verify(events).publishEvent(any(DepositChargeRequested.class));
        }

        @Test
        void finalReportWaitingForReviewPostponesTheDecision() {
            givenFinalReport(CheckInStatus.PENDING);

            service.decide(participant.getId(), NOW);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
        }
    }

    @Nested
    class Completion {

        @Test
        void approvedFinalReportCompletesGoalReleasesDepositAndCreditsCoins() {
            givenFinalReport(CheckInStatus.APPROVED);

            service.decide(participant.getId(), NOW);

            assertThat(participant.getGoal().getStatus()).isEqualTo(GoalStatus.COMPLETED);
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.COMPLETED);
            verify(coinService).credit(alice.getId(), COINS, CoinReason.GOAL_COMPLETED, participant.getGoal().getId());
            verify(events).publishEvent(new DepositReleaseRequested(participant.getId()));
            verify(events, never()).publishEvent(any(DepositChargeRequested.class));
        }

        @Test
        void lastParticipantToFinishClosesThePact() {
            givenFinalReport(CheckInStatus.APPROVED);
            given(participantRepository.existsByPactIdAndStatus(pact.getId(), ParticipantStatus.ACTIVE)).willReturn(false);

            service.decide(participant.getId(), NOW);

            assertThat(pact.getStatus()).isEqualTo(PactStatus.FINISHED);
        }

        @Test
        void pactStaysActiveWhileOthersArePursuingGoals() {
            givenFinalReport(CheckInStatus.APPROVED);

            service.decide(participant.getId(), NOW);

            assertThat(pact.getStatus()).isEqualTo(PactStatus.ACTIVE);
        }
    }

    @Nested
    class Idempotency {

        @Test
        void secondRunAfterFailureChargesNothingAgain() {
            service.decide(participant.getId(), NOW);
            service.decide(participant.getId(), NOW.plus(Duration.ofMinutes(5)));

            verify(events, times(1)).publishEvent(any(DepositChargeRequested.class));
            verify(events, times(1)).publishEvent(any(PactResultNotification.class));
        }

        @Test
        void secondRunAfterCompletionCreditsAndReleasesNothingAgain() {
            givenFinalReport(CheckInStatus.APPROVED);

            service.decide(participant.getId(), NOW);
            service.decide(participant.getId(), NOW.plus(Duration.ofMinutes(5)));

            verify(coinService, times(1)).credit(any(), eq(COINS), any(), any());
            verify(events, times(1)).publishEvent(any(DepositReleaseRequested.class));
        }

        @Test
        void pactThatIsNoLongerActiveIsSkipped() {
            pact.setStatus(PactStatus.FINISHED);

            service.decide(participant.getId(), NOW);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
            verifyNoInteractions(events, coinService);
        }
    }

    @Nested
    class Reminders {

        @Test
        void milestoneReminderIsSentOnce() {
            UUID id = milestone.getId();
            given(milestoneRepository.markReminderSent(id, NOW)).willReturn(1, 0);
            given(milestoneRepository.findWithOwnerById(id)).willReturn(Optional.of(milestone));
            milestone.getGoal().setUser(alice);

            service.remindMilestone(id, NOW);
            service.remindMilestone(id, NOW);

            verify(events, times(1)).publishEvent(new DeadlineReminderNotification("alice@example.com",
                    milestone.getGoal().getTitle(), "First step", milestone.getDeadline()));
        }

        @Test
        void goalReminderIsSentOnce() {
            given(participantRepository.markReminderSent(participant.getId(), NOW)).willReturn(1, 0);

            service.remindGoal(participant.getId(), NOW);
            service.remindGoal(participant.getId(), NOW);

            verify(events, times(1)).publishEvent(any(DeadlineReminderNotification.class));
        }
    }

    private void givenReport(UUID milestoneId, CheckInStatus status) {
        given(checkInRepository.existsByParticipantIdAndMilestoneIdAndStatusIn(participant.getId(), milestoneId,
                Set.of(status))).willReturn(true);
    }

    private void givenFinalReport(CheckInStatus status) {
        given(checkInRepository.existsByParticipantIdAndMilestoneIsNullAndStatusIn(participant.getId(),
                Set.of(status))).willReturn(true);
    }
}
