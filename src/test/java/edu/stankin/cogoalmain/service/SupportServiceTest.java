package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.SupportTicket;
import edu.stankin.cogoalmain.db.entity.User;
import edu.stankin.cogoalmain.db.entity.enums.SupportTicketStatus;
import edu.stankin.cogoalmain.db.repo.SupportTicketRepository;
import edu.stankin.cogoalmain.db.repo.UserRepository;
import edu.stankin.cogoalmain.exception.ForbiddenException;
import edu.stankin.cogoalmain.web.dto.support.CreateSupportTicketRequest;
import edu.stankin.cogoalmain.web.dto.support.SupportTicketResponse;
import edu.stankin.cogoalmain.web.mapper.GoalMapperImpl;
import edu.stankin.cogoalmain.web.mapper.SupportMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static edu.stankin.cogoalmain.support.PactFixtures.user;
import static edu.stankin.cogoalmain.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock
    private SupportTicketRepository ticketRepository;
    @Mock
    private UserRepository userRepository;

    private SupportService service;

    private final User alice = user("alice");
    private final User bob = user("bob");

    @BeforeEach
    void setUp() {
        service = new SupportService(ticketRepository, userRepository, new SupportMapperImpl(new GoalMapperImpl()));
    }

    @Test
    void newTicketIsOpen() {
        given(userRepository.findById(alice.getId())).willReturn(Optional.of(alice));
        given(ticketRepository.save(any())).willAnswer(invocation -> withId(invocation.<SupportTicket>getArgument(0)));

        SupportTicketResponse response = service.create(alice.getId(),
                new CreateSupportTicketRequest("Deposit stuck", "Paid yesterday, still pending"));

        assertThat(response.status()).isEqualTo(SupportTicketStatus.OPEN);
        assertThat(response.subject()).isEqualTo("Deposit stuck");
    }

    @Test
    void usersSeeOnlyTheirOwnTickets() {
        SupportTicket ticket = ticketOf(alice);

        assertThat(service.getMyTicket(alice.getId(), ticket.getId()).id()).isEqualTo(ticket.getId());
        assertThatThrownBy(() -> service.getMyTicket(bob.getId(), ticket.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminChangesStatusAndSeesAuthor() {
        SupportTicket ticket = ticketOf(alice);
        given(ticketRepository.saveAndFlush(ticket)).willReturn(ticket);

        var response = service.changeStatus(ticket.getId(), SupportTicketStatus.IN_PROGRESS);

        assertThat(response.status()).isEqualTo(SupportTicketStatus.IN_PROGRESS);
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.userEmail()).isEqualTo("alice@example.com");
    }

    private SupportTicket ticketOf(User owner) {
        SupportTicket ticket = withId(new SupportTicket());
        ticket.setUser(owner);
        ticket.setSubject("Help");
        ticket.setStatus(SupportTicketStatus.OPEN);
        given(ticketRepository.findWithUserById(ticket.getId())).willReturn(Optional.of(ticket));
        return ticket;
    }
}
