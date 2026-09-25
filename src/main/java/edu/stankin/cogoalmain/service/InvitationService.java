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
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.service.event.InvitationCreated;
import edu.stankin.cogoalmain.web.dto.pact.InvitationResponse;
import edu.stankin.cogoalmain.web.dto.pact.JoinPactRequest;
import edu.stankin.cogoalmain.web.dto.pact.PactResponse;
import edu.stankin.cogoalmain.web.mapper.PactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Invitations to OPEN pacts. Any current participant may invite; the invitee joins by accepting
 * with their own DRAFT goal and deposit, exactly as with a direct join.
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final PactService pactService;
    private final PactInvitationRepository invitationRepository;
    private final PactParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final PactMapper pactMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public InvitationResponse invite(UUID inviterId, UUID pactId, UUID inviteeId) {
        Pact pact = pactService.lock(pactId);
        if (pact.getStatus() != PactStatus.OPEN) {
            throw new BusinessRuleException("Invitations can only be sent while the pact is open");
        }
        if (!isMember(pactId, inviterId)) {
            throw new ForbiddenException("Only participants of the pact can invite");
        }
        if (inviterId.equals(inviteeId)) {
            throw new BusinessRuleException("You cannot invite yourself");
        }
        User invitee = userRepository.findById(inviteeId).orElseThrow(() -> NotFoundException.of("User", inviteeId));
        if (isMember(pactId, inviteeId)) {
            throw new BusinessRuleException("The user is already a participant of this pact");
        }
        if (invitationRepository.existsByPactIdAndInviteeIdAndStatus(pactId, inviteeId, InvitationStatus.PENDING)) {
            throw new BusinessRuleException("The user already has a pending invitation to this pact");
        }
        User inviter = userRepository.findById(inviterId)
                .orElseThrow(() -> NotFoundException.of("User", inviterId));

        PactInvitation invitation = new PactInvitation();
        invitation.setPact(pact);
        invitation.setInviter(inviter);
        invitation.setInvitee(invitee);
        invitation.setStatus(InvitationStatus.PENDING);
        invitationRepository.save(invitation);

        events.publishEvent(new InvitationCreated(invitation.getId(), pactId, invitee.getEmail(), inviter.getUsername()));
        return pactMapper.toResponse(invitation);
    }

    /** Invitations addressed to the user, optionally with the given status. */
    @Transactional(readOnly = true)
    public Page<InvitationResponse> getMyInvitations(UUID userId, InvitationStatus status, Pageable pageable) {
        Page<PactInvitation> invitations = status == null
                ? invitationRepository.findByInviteeId(userId, pageable)
                : invitationRepository.findByInviteeIdAndStatus(userId, status, pageable);
        return invitations.map(pactMapper::toResponse);
    }

    /** Joins the pact with the given goal and deposit; the invitation becomes ACCEPTED. */
    @Transactional
    public PactResponse accept(UUID userId, UUID invitationId, JoinPactRequest request) {
        Pact pact = lockPactOf(invitationId);
        requirePendingOwn(loadInvitation(invitationId), userId);

        pactService.enroll(pact, userId, request);
        return pactService.toResponse(pact);
    }

    @Transactional
    public InvitationResponse decline(UUID userId, UUID invitationId) {
        lockPactOf(invitationId);
        PactInvitation invitation = requirePendingOwn(loadInvitation(invitationId), userId);

        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setRespondedAt(clock.instant());
        return pactMapper.toResponse(invitation);
    }

    // Lock first, load the invitation afterwards, so its status is read under the lock
    private Pact lockPactOf(UUID invitationId) {
        UUID pactId = invitationRepository.findPactIdById(invitationId)
                .orElseThrow(() -> NotFoundException.of("Invitation", invitationId));
        return pactService.lock(pactId);
    }

    private PactInvitation loadInvitation(UUID invitationId) {
        return invitationRepository.findDetailedById(invitationId)
                .orElseThrow(() -> NotFoundException.of("Invitation", invitationId));
    }

    private static PactInvitation requirePendingOwn(PactInvitation invitation, UUID userId) {
        if (!invitation.getInvitee().getId().equals(userId)) {
            throw new ForbiddenException("This invitation is addressed to another user");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessRuleException("The invitation is already " + invitation.getStatus());
        }
        return invitation;
    }

    private boolean isMember(UUID pactId, UUID userId) {
        return participantRepository.existsByPactIdAndUserIdAndStatusNot(pactId, userId, ParticipantStatus.LEFT);
    }
}
