package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.db.entity.enums.InvitationStatus;
import edu.stankin.cogoalmain.service.InvitationService;
import edu.stankin.cogoalmain.web.dto.pact.CreateInvitationRequest;
import edu.stankin.cogoalmain.web.dto.pact.InvitationResponse;
import edu.stankin.cogoalmain.web.dto.pact.JoinPactRequest;
import edu.stankin.cogoalmain.web.dto.pact.PactResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Inviting users to open pacts and answering invitations")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping("/pacts/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite a user to an open pact (any participant can invite)")
    public InvitationResponse invite(@CurrentUserId UUID userId, @PathVariable UUID id,
                                     @Valid @RequestBody CreateInvitationRequest request) {
        return invitationService.invite(userId, id, request.inviteeId());
    }

    @GetMapping("/invitations/my")
    @Operation(summary = "Invitations addressed to the user, newest first, optionally by status")
    public Page<InvitationResponse> my(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) InvitationStatus status,
            @ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return invitationService.getMyInvitations(userId, status, pageable);
    }

    @PostMapping("/invitations/{id}/accept")
    @Operation(summary = "Accept an invitation: join the pact with an own DRAFT goal and a deposit")
    public PactResponse accept(@CurrentUserId UUID userId, @PathVariable UUID id,
                               @Valid @RequestBody JoinPactRequest request) {
        return invitationService.accept(userId, id, request);
    }

    @PostMapping("/invitations/{id}/decline")
    @Operation(summary = "Decline an invitation")
    public InvitationResponse decline(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return invitationService.decline(userId, id);
    }
}
