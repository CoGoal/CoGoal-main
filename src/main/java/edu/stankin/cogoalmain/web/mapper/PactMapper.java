package edu.stankin.cogoalmain.web.mapper;

import edu.stankin.cogoalmain.db.entity.Charity;
import edu.stankin.cogoalmain.db.entity.Message;
import edu.stankin.cogoalmain.db.entity.Pact;
import edu.stankin.cogoalmain.db.entity.PactInvitation;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.web.dto.pact.CharityRef;
import edu.stankin.cogoalmain.web.dto.pact.DepositResponse;
import edu.stankin.cogoalmain.web.dto.pact.InvitationResponse;
import edu.stankin.cogoalmain.web.dto.pact.MessageResponse;
import edu.stankin.cogoalmain.web.dto.pact.PactResponse;
import edu.stankin.cogoalmain.web.dto.pact.PactSummaryResponse;
import edu.stankin.cogoalmain.web.dto.pact.ParticipantResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Pacts, participants, invitations and chat. Goals and users are mapped by {@link GoalMapper}.
 */
@Mapper(config = CentralMapperConfig.class, uses = GoalMapper.class)
public interface PactMapper {

    /** Requires {@code goal} and {@code charity} of the pact to be loaded. */
    @Mapping(target = "id", source = "pact.id")
    @Mapping(target = "status", source = "pact.status")
    @Mapping(target = "creatorId", source = "pact.goal.user.id")
    @Mapping(target = "goalId", source = "pact.goal.id")
    @Mapping(target = "charity", source = "pact.charity")
    @Mapping(target = "createdAt", source = "pact.createdAt")
    @Mapping(target = "participants", source = "participants")
    PactResponse toResponse(Pact pact, List<PactParticipant> participants);

    /** Requires {@code user}, {@code goal} and {@code goal.category} to be loaded. */
    ParticipantResponse toResponse(PactParticipant participant);

    /** Requires {@code pact} and {@code pact.charity} to be loaded. */
    @Mapping(target = "id", source = "pact.id")
    @Mapping(target = "status", source = "pact.status")
    @Mapping(target = "charity", source = "pact.charity")
    @Mapping(target = "createdAt", source = "pact.createdAt")
    @Mapping(target = "participantId", source = "id")
    @Mapping(target = "myStatus", source = "status")
    @Mapping(target = "myDepositStatus", source = "depositStatus")
    PactSummaryResponse toSummary(PactParticipant participant);

    @Mapping(target = "participantId", source = "id")
    @Mapping(target = "amount", source = "depositAmount")
    @Mapping(target = "currency", source = "depositCurrency")
    @Mapping(target = "participantStatus", source = "status")
    DepositResponse toDeposit(PactParticipant participant);

    CharityRef toRef(Charity charity);

    /** Requires {@code pact}, {@code pact.goal}, {@code inviter} and {@code invitee} to be loaded. */
    @Mapping(target = "pactId", source = "pact.id")
    @Mapping(target = "pactStatus", source = "pact.status")
    @Mapping(target = "goalTitle", source = "pact.goal.title")
    InvitationResponse toResponse(PactInvitation invitation);

    /** Requires {@code sender} to be loaded. */
    MessageResponse toResponse(Message message);
}
