package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Goal;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactInvitation;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.User;
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
import edu.stankin.cogoalmain.service.event.DepositReleaseRequested;
import edu.stankin.cogoalmain.web.dto.pact.CreatePactRequest;
import edu.stankin.cogoalmain.web.dto.pact.JoinPactRequest;
import edu.stankin.cogoalmain.web.dto.pact.PactResponse;
import edu.stankin.cogoalmain.web.mapper.GoalMapperImpl;
import edu.stankin.cogoalmain.web.mapper.PactMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static edu.stankin.cogoalmain.support.PactFixtures.goal;
import static edu.stankin.cogoalmain.support.PactFixtures.pact;
import static edu.stankin.cogoalmain.support.PactFixtures.participant;
import static edu.stankin.cogoalmain.support.PactFixtures.user;
import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PactServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final BigDecimal DEPOSIT = new BigDecimal("500.00");

    @Mock
    private PactRepository pactRepository;
    @Mock
    private PactParticipantRepository participantRepository;
    @Mock
    private PactInvitationRepository invitationRepository;
    @Mock
    private GoalService goalService;
    @Mock
    private CharityService charityService;
    @Mock
    private ApplicationEventPublisher events;

    private PactService service;

    private final User creator = user("creator");
    private final User alice = user("alice");
    private final User bob = user("bob");

    @BeforeEach
    void setUp() {
        service = new PactService(pactRepository, participantRepository, invitationRepository, goalService,
                charityService, new PactMapperImpl(new GoalMapperImpl()), events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createPublishesGoalAndEnrollsCreator() {
        Goal goal = goal(creator, GoalStatus.DRAFT);
        Pact template = pact(creator, PactStatus.OPEN);
        given(goalService.requireOwnDraft(creator.getId(), goal.getId())).willReturn(goal);
        given(charityService.requireActive(template.getCharity().getId())).willReturn(template.getCharity());
        given(pactRepository.save(any())).willAnswer(invocation -> withId(invocation.<Pact>getArgument(0)));

        service.create(creator.getId(), new CreatePactRequest(goal.getId(), template.getCharity().getId(), DEPOSIT));

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.IN_PACT);
        verify(participantRepository).save(org.mockito.ArgumentMatchers.<PactParticipant>argThat(p ->
                p.getUser() == creator
                        && p.getStatus() == ParticipantStatus.PENDING_DEPOSIT
                        && p.getDepositStatus() == DepositStatus.NOT_STARTED
                        && p.getDepositAmount().equals(DEPOSIT)
                        && p.getPact().getStatus() == PactStatus.OPEN));
    }

    @Nested
    class Join {

        @Test
        void newParticipantWaitsForDepositAndPendingInvitationIsAccepted() {
            Pact pact = lockedPact(PactStatus.OPEN);
            Goal goal = goal(alice, GoalStatus.DRAFT);
            PactInvitation invitation = new PactInvitation();
            invitation.setStatus(InvitationStatus.PENDING);
            given(goalService.requireOwnDraft(alice.getId(), goal.getId())).willReturn(goal);
            given(participantRepository.findByPactIdAndUserId(pact.getId(), alice.getId())).willReturn(Optional.empty());
            given(invitationRepository.findByPactIdAndInviteeIdAndStatus(pact.getId(), alice.getId(),
                    InvitationStatus.PENDING)).willReturn(Optional.of(invitation));

            service.join(alice.getId(), pact.getId(), new JoinPactRequest(goal.getId(), DEPOSIT));

            assertThat(goal.getStatus()).isEqualTo(GoalStatus.IN_PACT);
            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
            verify(participantRepository).save(org.mockito.ArgumentMatchers.<PactParticipant>argThat(p ->
                    p.getStatus() == ParticipantStatus.PENDING_DEPOSIT && p.getGoal() == goal));
        }

        @Test
        void cannotJoinPactThatIsNotOpen() {
            Pact pact = lockedPact(PactStatus.ACTIVE);

            assertThatThrownBy(() -> service.join(alice.getId(), pact.getId(), request()))
                    .isInstanceOf(BusinessRuleException.class);
            verify(participantRepository, never()).save(any());
        }

        @Test
        void cannotJoinTwice() {
            Pact pact = lockedPact(PactStatus.OPEN);
            PactParticipant existing = participant(pact, alice, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.NOT_STARTED);
            given(goalService.requireOwnDraft(any(), any())).willReturn(goal(alice, GoalStatus.DRAFT));
            given(participantRepository.findByPactIdAndUserId(pact.getId(), alice.getId()))
                    .willReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.join(alice.getId(), pact.getId(), request()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already a participant");
        }

        @Test
        void formerParticipantRejoinsWithTheirOldRecord() {
            Pact pact = lockedPact(PactStatus.OPEN);
            PactParticipant left = participant(pact, alice, ParticipantStatus.LEFT, DepositStatus.RELEASED);
            Goal newGoal = goal(alice, GoalStatus.DRAFT);
            given(goalService.requireOwnDraft(alice.getId(), newGoal.getId())).willReturn(newGoal);
            given(participantRepository.findByPactIdAndUserId(pact.getId(), alice.getId())).willReturn(Optional.of(left));

            service.join(alice.getId(), pact.getId(), new JoinPactRequest(newGoal.getId(), DEPOSIT));

            assertThat(left.getStatus()).isEqualTo(ParticipantStatus.PENDING_DEPOSIT);
            assertThat(left.getDepositStatus()).isEqualTo(DepositStatus.NOT_STARTED);
            assertThat(left.getGoal()).isSameAs(newGoal);
        }

        @Test
        void formerParticipantCannotRejoinWhileOldDepositIsInFlight() {
            Pact pact = lockedPact(PactStatus.OPEN);
            PactParticipant left = participant(pact, alice, ParticipantStatus.LEFT, DepositStatus.HELD);
            given(goalService.requireOwnDraft(any(), any())).willReturn(goal(alice, GoalStatus.DRAFT));
            given(participantRepository.findByPactIdAndUserId(pact.getId(), alice.getId())).willReturn(Optional.of(left));

            assertThatThrownBy(() -> service.join(alice.getId(), pact.getId(), request()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("still being processed");
            assertThat(left.getStatus()).isEqualTo(ParticipantStatus.LEFT);
        }
    }

    @Nested
    class Leave {

        @Test
        void leavingReturnsGoalToDraftAndReleasesHeldDeposit() {
            Pact pact = lockedPact(PactStatus.OPEN);
            PactParticipant member = participant(pact, alice, ParticipantStatus.ACTIVE, DepositStatus.HELD);
            given(participantRepository.findByPactIdAndUserId(pact.getId(), alice.getId())).willReturn(Optional.of(member));

            service.leave(alice.getId(), pact.getId());

            assertThat(member.getStatus()).isEqualTo(ParticipantStatus.LEFT);
            assertThat(member.getGoal().getStatus()).isEqualTo(GoalStatus.DRAFT);
            verify(events).publishEvent(new DepositReleaseRequested(member.getId()));
        }

        @Test
        void leavingWithoutHeldDepositReleasesNothing() {
            Pact pact = lockedPact(PactStatus.OPEN);
            PactParticipant member = participant(pact, alice, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.PENDING);
            given(participantRepository.findByPactIdAndUserId(pact.getId(), alice.getId())).willReturn(Optional.of(member));

            service.leave(alice.getId(), pact.getId());

            verify(events, never()).publishEvent(any());
        }

        @Test
        void cannotLeaveActivePact() {
            Pact pact = lockedPact(PactStatus.ACTIVE);

            assertThatThrownBy(() -> service.leave(alice.getId(), pact.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("before it starts");
        }

        @Test
        void creatorCannotLeave() {
            Pact pact = lockedPact(PactStatus.OPEN);

            assertThatThrownBy(() -> service.leave(creator.getId(), pact.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("cancel");
        }
    }

    @Nested
    class Start {

        @Test
        void startsWithTwoActiveAndDropsUnpaid() {
            Pact pact = lockedPact(PactStatus.OPEN);
            PactParticipant creatorP = participant(pact, creator, ParticipantStatus.ACTIVE, DepositStatus.HELD);
            PactParticipant aliceP = participant(pact, alice, ParticipantStatus.ACTIVE, DepositStatus.HELD);
            PactParticipant bobP = participant(pact, bob, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.PENDING);
            given(participantRepository.findAllInPact(pact.getId())).willReturn(List.of(creatorP, aliceP, bobP));

            PactResponse response = service.start(creator.getId(), pact.getId());

            assertThat(pact.getStatus()).isEqualTo(PactStatus.ACTIVE);
            assertThat(bobP.getStatus()).isEqualTo(ParticipantStatus.LEFT);
            assertThat(bobP.getGoal().getStatus()).isEqualTo(GoalStatus.DRAFT);
            assertThat(aliceP.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
            assertThat(response.participants()).hasSize(2);
            verify(invitationRepository).cancelPending(pact.getId(), NOW);
        }

        @Test
        void needsTwoParticipantsWithHeldDeposit() {
            Pact pact = lockedPact(PactStatus.OPEN);
            given(participantRepository.findAllInPact(pact.getId())).willReturn(List.of(
                    participant(pact, creator, ParticipantStatus.ACTIVE, DepositStatus.HELD),
                    participant(pact, alice, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.PENDING)));

            assertThatThrownBy(() -> service.start(creator.getId(), pact.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("At least 2");
            assertThat(pact.getStatus()).isEqualTo(PactStatus.OPEN);
        }

        @Test
        void onlyCreatorCanStart() {
            Pact pact = lockedPact(PactStatus.OPEN);

            assertThatThrownBy(() -> service.start(alice.getId(), pact.getId()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void activePactCannotBeStartedAgain() {
            Pact pact = lockedPact(PactStatus.ACTIVE);

            assertThatThrownBy(() -> service.start(creator.getId(), pact.getId()))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class Cancel {

        @Test
        void everyoneIncludingCreatorLeavesAndHeldDepositsAreReleased() {
            Pact pact = lockedPact(PactStatus.OPEN);
            PactParticipant creatorP = participant(pact, creator, ParticipantStatus.ACTIVE, DepositStatus.HELD);
            PactParticipant aliceP = participant(pact, alice, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.NOT_STARTED);
            given(participantRepository.findAllInPact(pact.getId())).willReturn(List.of(creatorP, aliceP));

            service.cancel(creator.getId(), pact.getId());

            assertThat(pact.getStatus()).isEqualTo(PactStatus.CANCELLED);
            assertThat(List.of(creatorP, aliceP)).allMatch(p -> p.getStatus() == ParticipantStatus.LEFT);
            assertThat(List.of(creatorP, aliceP)).allMatch(p -> p.getGoal().getStatus() == GoalStatus.DRAFT);
            verify(events).publishEvent(new DepositReleaseRequested(creatorP.getId()));
            verify(events, never()).publishEvent(new DepositReleaseRequested(aliceP.getId()));
            verify(invitationRepository).cancelPending(eq(pact.getId()), any());
        }

        @Test
        void startedPactCannotBeCancelled() {
            Pact pact = lockedPact(PactStatus.ACTIVE);

            assertThatThrownBy(() -> service.cancel(creator.getId(), pact.getId()))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void onlyCreatorCanCancel() {
            Pact pact = lockedPact(PactStatus.OPEN);

            assertThatThrownBy(() -> service.cancel(alice.getId(), pact.getId()))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void finishedPactIsHiddenFromOutsiders() {
        Pact pact = pact(creator, PactStatus.FINISHED);
        given(pactRepository.findDetailedById(pact.getId())).willReturn(Optional.of(pact));
        given(participantRepository.findAllInPact(pact.getId())).willReturn(List.of(
                participant(pact, creator, ParticipantStatus.COMPLETED, DepositStatus.RELEASED)));

        assertThatThrownBy(() -> service.get(UUID.randomUUID(), pact.getId())).isInstanceOf(ForbiddenException.class);
        assertThat(service.get(creator.getId(), pact.getId()).status()).isEqualTo(PactStatus.FINISHED);
    }

    private Pact lockedPact(PactStatus status) {
        Pact pact = pact(creator, status);
        given(pactRepository.findForUpdate(pact.getId())).willReturn(Optional.of(pact));
        return pact;
    }

    private static JoinPactRequest request() {
        return new JoinPactRequest(UUID.randomUUID(), DEPOSIT);
    }
}
