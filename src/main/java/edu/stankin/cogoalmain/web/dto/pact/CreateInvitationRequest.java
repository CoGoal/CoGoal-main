package edu.stankin.cogoalmain.web.dto.pact;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateInvitationRequest(@NotNull UUID inviteeId) {
}
