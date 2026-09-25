package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.client.payment.PaymentClient;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.DepositStatus;
import edu.stankin.cogoalmain.db.entity.enums.PactStatus;
import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import edu.stankin.cogoalmain.db.repo.PactParticipantRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.ExternalServiceException;
import edu.stankin.cogoalmain.service.event.DepositReleaseRequested;
import edu.stankin.cogoalmain.web.dto.internal.PaymentEventRequest;
import edu.stankin.cogoalmain.web.dto.internal.PaymentEventRequest.Event;
import edu.stankin.cogoalmain.web.dto.pact.DepositStartResponse;
import edu.stankin.cogoalmain.web.mapper.GoalMapperImpl;
import edu.stankin.cogoalmain.web.mapper.PactMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static edu.stankin.cogoalmain.support.PactFixtures.pact;
import static edu.stankin.cogoalmain.support.PactFixtures.participant;
import static edu.stankin.cogoalmain.support.PactFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock
    private PactService pactService;
    @Mock
    private PactParticipantRepository participantRepository;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private ApplicationEventPublisher events;

    private DepositService service;

    private final User creator = user("creator");
    private final User alice = user("alice");

    @BeforeEach
    void setUp() {
        service = new DepositService(pactService, participantRepository, new PactMapperImpl(new GoalMapperImpl()),
                paymentClient, events, new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Nested
    class StartDeposit {

        @Test
        void marksPendingAndReturnsPaymentUrl() {
            PactParticipant p = own(PactStatus.OPEN, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.NOT_STARTED);
            given(paymentClient.holdDeposit(p.getId(), alice.getId(), p.getDepositAmount(), "RUB"))
                    .willReturn("https://pay/1");

            DepositStartResponse response = service.startDeposit(alice.getId(), p.getPact().getId());

            assertThat(p.getDepositStatus()).isEqualTo(DepositStatus.PENDING);
            assertThat(response.paymentUrl()).isEqualTo("https://pay/1");
        }

        @Test
        void canRetryAfterFailure() {
            PactParticipant p = own(PactStatus.OPEN, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.FAILED);
            given(paymentClient.holdDeposit(any(), any(), any(), any())).willReturn("https://pay/2");

            service.startDeposit(alice.getId(), p.getPact().getId());

            assertThat(p.getDepositStatus()).isEqualTo(DepositStatus.PENDING);
        }

        @Test
        void heldDepositCannotBePaidAgain() {
            PactParticipant p = own(PactStatus.OPEN, ParticipantStatus.ACTIVE, DepositStatus.HELD);

            assertThatThrownBy(() -> service.startDeposit(alice.getId(), p.getPact().getId()))
                    .isInstanceOf(BusinessRuleException.class);
            verifyNoInteractions(paymentClient);
        }

        @Test
        void paymentServiceFailureKeepsPendingSoUserCanRetry() {
            PactParticipant p = own(PactStatus.OPEN, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.NOT_STARTED);
            given(paymentClient.holdDeposit(any(), any(), any(), any())).willThrow(new IllegalStateException("down"));

            assertThatThrownBy(() -> service.startDeposit(alice.getId(), p.getPact().getId()))
                    .isInstanceOf(ExternalServiceException.class);
            assertThat(p.getDepositStatus()).isEqualTo(DepositStatus.PENDING);
        }

        private PactParticipant own(PactStatus pactStatus, ParticipantStatus status, DepositStatus deposit) {
            Pact pact = pact(creator, pactStatus);
            PactParticipant p = participant(pact, alice, status, deposit);
            given(pactService.lock(pact.getId())).willReturn(pact);
            given(participantRepository.findByPactIdAndUserId(pact.getId(), alice.getId())).willReturn(Optional.of(p));
            return p;
        }
    }

    @Nested
    class PaymentEvents {

        @Test
        void heldActivatesWaitingParticipant() {
            PactParticipant p = stored(PactStatus.OPEN, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.PENDING);

            service.handleEvent(new PaymentEventRequest(p.getId(), Event.HELD));

            assertThat(p.getDepositStatus()).isEqualTo(DepositStatus.HELD);
            assertThat(p.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
            verifyNoInteractions(events);
        }

        @Test
        void repeatedHeldChangesNothing() {
            PactParticipant p = stored(PactStatus.OPEN, ParticipantStatus.ACTIVE, DepositStatus.HELD);

            service.handleEvent(new PaymentEventRequest(p.getId(), Event.HELD));

            assertThat(p.getStatus()).isEqualTo(ParticipantStatus.ACTIVE);
            verifyNoInteractions(events);
        }

        @Test
        void heldAfterParticipantLeftIsReleasedImmediately() {
            PactParticipant p = stored(PactStatus.OPEN, ParticipantStatus.LEFT, DepositStatus.PENDING);

            service.handleEvent(new PaymentEventRequest(p.getId(), Event.HELD));

            assertThat(p.getStatus()).isEqualTo(ParticipantStatus.LEFT);
            verify(events).publishEvent(new DepositReleaseRequested(p.getId()));
        }

        @ParameterizedTest(name = "{1} after {0} is ignored")
        @CsvSource({
                "RELEASED, HELD",
                "CHARGED, RELEASED",
                "HELD, FAILED",
                "PENDING, CHARGED"
        })
        void staleOrImpossibleEventsAreIgnored(DepositStatus current, Event event) {
            PactParticipant p = stored(PactStatus.OPEN, ParticipantStatus.PENDING_DEPOSIT, current);

            service.handleEvent(new PaymentEventRequest(p.getId(), event));

            assertThat(p.getDepositStatus()).isEqualTo(current);
            assertThat(p.getStatus()).isEqualTo(ParticipantStatus.PENDING_DEPOSIT);
        }

        @Test
        void failedLeavesParticipantWaitingSoTheyCanRetry() {
            PactParticipant p = stored(PactStatus.OPEN, ParticipantStatus.PENDING_DEPOSIT, DepositStatus.PENDING);

            service.handleEvent(new PaymentEventRequest(p.getId(), Event.FAILED));

            assertThat(p.getDepositStatus()).isEqualTo(DepositStatus.FAILED);
            assertThat(p.getStatus()).isEqualTo(ParticipantStatus.PENDING_DEPOSIT);
        }

        @Test
        void chargedAndReleasedAreApplied() {
            PactParticipant charged = stored(PactStatus.ACTIVE, ParticipantStatus.FAILED, DepositStatus.HELD);
            service.handleEvent(new PaymentEventRequest(charged.getId(), Event.CHARGED));
            assertThat(charged.getDepositStatus()).isEqualTo(DepositStatus.CHARGED);

            PactParticipant released = stored(PactStatus.OPEN, ParticipantStatus.LEFT, DepositStatus.HELD);
            service.handleEvent(new PaymentEventRequest(released.getId(), Event.RELEASED));
            assertThat(released.getDepositStatus()).isEqualTo(DepositStatus.RELEASED);

            verify(events, never()).publishEvent(any());
        }

        private PactParticipant stored(PactStatus pactStatus, ParticipantStatus status, DepositStatus deposit) {
            Pact pact = pact(creator, pactStatus);
            PactParticipant p = participant(pact, alice, status, deposit);
            given(participantRepository.findPactIdById(p.getId())).willReturn(Optional.of(pact.getId()));
            given(pactService.lock(pact.getId())).willReturn(pact);
            given(participantRepository.findById(p.getId())).willReturn(Optional.of(p));
            return p;
        }
    }
}
