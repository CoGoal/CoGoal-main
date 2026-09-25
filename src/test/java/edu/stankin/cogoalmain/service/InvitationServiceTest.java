package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactInvitation;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.InvitationStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.repo.PactInvitationRepository;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.db.repo.UserRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.service.event.InvitationCreated;
import edu.stankin.cogoalmain.web.dto.pact.JoinPactRequest;
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
import java.util.Optional;
import java.util.UUID;

import static edu.stankin.cogoalmain.support.PactFixtures.pact;
import static edu.stankin.cogoalmain.support.PactFixtures.user;
import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Mock
    private PactService pactService;
    @Mock
    private PactInvitationRepository invitationRepository;
    @Mock
    private PactParticipantRepository participantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher events;

    private InvitationService service;

    private final User creator = user("creator");
    private final User alice = user("alice");
    private Pact pact;

    @BeforeEach
    void setUp() {
        service = new InvitationService(pactService, invitationRepository, participantRepository, userRepository,
                new PactMapperImpl(new GoalMapperImpl()), events, Clock.fixed(NOW, ZoneOffset.UTC));
        pact = pact(creator, PactStatus.OPEN);
    }

    @Nested
    class Invite {

        @Test
        void participantInvitesUserAndEmailIsSentAfterCommit() {
            given(pactService.lock(pact.getId())).willReturn(pact);
            givenMember(creator, true);
            givenMember(alice, false);
            given(userRepository.findById(alice.getId())).willReturn(Optional.of(alice));
            given(userRepository.findById(creator.getId())).willReturn(Optional.of(creator));
            given(invitationRepository.save(any())).willAnswer(i -> withId(i.<PactInvitation>getArgument(0)));

            var response = service.invite(creator.getId(), pact.getId(), alice.getId());

            assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
            assertThat(response.goalTitle()).isEqualTo(pact.getGoal().getTitle());
            verify(events).publishEvent(new InvitationCreated(response.id(), pact.getId(),
                    "alice@example.com", "creator"));
        }

        @Test
        void outsidersCannotInvite() {
            given(pactService.lock(pact.getId())).willReturn(pact);
            givenMember(alice, false);

            assertThatThrownBy(() -> service.invite(alice.getId(), pact.getId(), UUID.randomUUID()))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        void cannotInviteSelf() {
            given(pactService.lock(pact.getId())).willReturn(pact);
            givenMember(creator, true);

            assertThatThrownBy(() -> service.invite(creator.getId(), pact.getId(), creator.getId()))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void onlyOnePendingInvitationPerUser() {
            given(pactService.lock(pact.getId())).willReturn(pact);
            givenMember(creator, true);
            givenMember(alice, false);
            given(userRepository.findById(alice.getId())).willReturn(Optional.of(alice));
            given(invitationRepository.existsByPactIdAndInviteeIdAndStatus(pact.getId(), alice.getId(),
                    InvitationStatus.PENDING)).willReturn(true);

            assertThatThrownBy(() -> service.invite(creator.getId(), pact.getId(), alice.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("pending");
            verify(invitationRepository, never()).save(any());
        }

        @Test
        void noInvitationsOnceThePactStarted() {
            pact.setStatus(PactStatus.ACTIVE);
            given(pactService.lock(pact.getId())).willReturn(pact);

            assertThatThrownBy(() -> service.invite(creator.getId(), pact.getId(), alice.getId()))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class Respond {

        @Test
        void acceptJoinsThePactUnderLock() {
            PactInvitation invitation = storedInvitation(InvitationStatus.PENDING);
            JoinPactRequest request = new JoinPactRequest(UUID.randomUUID(), BigDecimal.TEN);

            service.accept(alice.getId(), invitation.getId(), request);

            verify(pactService).enroll(pact, alice.getId(), request);
        }

        @Test
        void onlyInviteeCanAnswer() {
            PactInvitation invitation = storedInvitation(InvitationStatus.PENDING);

            assertThatThrownBy(() -> service.decline(creator.getId(), invitation.getId()))
                    .isInstanceOf(ForbiddenException.class);
            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        void answeredInvitationCannotBeAcceptedLater() {
            PactInvitation invitation = storedInvitation(InvitationStatus.DECLINED);

            assertThatThrownBy(() -> service.accept(alice.getId(), invitation.getId(),
                    new JoinPactRequest(UUID.randomUUID(), BigDecimal.TEN)))
                    .isInstanceOf(BusinessRuleException.class);
            verify(pactService, never()).enroll(any(), any(), any());
        }

        @Test
        void declineRecordsTheAnswer() {
            PactInvitation invitation = storedInvitation(InvitationStatus.PENDING);

            service.decline(alice.getId(), invitation.getId());

            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
        }

        private PactInvitation storedInvitation(InvitationStatus status) {
            PactInvitation invitation = withId(new PactInvitation());
            invitation.setPact(pact);
            invitation.setInviter(creator);
            invitation.setInvitee(alice);
            invitation.setStatus(status);
            given(invitationRepository.findPactIdById(invitation.getId())).willReturn(Optional.of(pact.getId()));
            given(pactService.lock(pact.getId())).willReturn(pact);
            given(invitationRepository.findDetailedById(invitation.getId())).willReturn(Optional.of(invitation));
            return invitation;
        }
    }

    private void givenMember(User user, boolean member) {
        given(participantRepository.existsByPactIdAndUserIdAndStatusNot(pact.getId(), user.getId(),
                ParticipantStatus.LEFT)).willReturn(member);
    }
}
