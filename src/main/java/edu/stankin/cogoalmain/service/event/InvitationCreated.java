package edu.stankin.cogoalmain.service.event;

import java.util.UUID;

public record InvitationCreated(UUID invitationId, UUID pactId, String inviteeEmail, String inviterUsername) {
}
