package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.service.DepositService;
import edu.stankin.cogoalmain.service.PactService;
import edu.stankin.cogoalmain.web.dto.pact.CreatePactRequest;
import edu.stankin.cogoalmain.web.dto.pact.DepositResponse;
import edu.stankin.cogoalmain.web.dto.pact.DepositStartResponse;
import edu.stankin.cogoalmain.web.dto.pact.JoinPactRequest;
import edu.stankin.cogoalmain.web.dto.pact.PactResponse;
import edu.stankin.cogoalmain.web.dto.pact.PactSummaryResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pacts")
@RequiredArgsConstructor
@Tag(name = "Pacts", description = "Creating, joining, starting and leaving pacts; deposits")
public class PactController {

    private final PactService pactService;
    private final DepositService depositService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish an own DRAFT goal as a pact; the creator becomes its first participant")
    public PactResponse create(@CurrentUserId UUID userId, @Valid @RequestBody CreatePactRequest request) {
        return pactService.create(userId, request);
    }

    @GetMapping("/my")
    @Operation(summary = "Pacts the user takes or took part in, with their own status in each")
    public Page<PactSummaryResponse> my(
            @CurrentUserId UUID userId,
            @ParameterObject @SortDefault(sort = "joinedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return pactService.getMyPacts(userId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Pact with participants, their goals and statuses",
            description = "An open pact is visible to everyone; afterwards only to its participants")
    public PactResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return pactService.get(userId, id);
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Join an open pact with an own DRAFT goal and a deposit")
    public PactResponse join(@CurrentUserId UUID userId, @PathVariable UUID id,
                             @Valid @RequestBody JoinPactRequest request) {
        return pactService.join(userId, id, request);
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Leave a pact before it starts; a held deposit is returned")
    public void leave(@CurrentUserId UUID userId, @PathVariable UUID id) {
        pactService.leave(userId, id);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start the pact (creator only, at least 2 participants with a held deposit)",
            description = "Participants who have not paid are dropped; pending invitations are cancelled")
    public PactResponse start(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return pactService.start(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a pact that has not started (creator only); all deposits are returned")
    public void cancel(@CurrentUserId UUID userId, @PathVariable UUID id) {
        pactService.cancel(userId, id);
    }

    @PostMapping("/{id}/deposit")
    @Operation(summary = "Start paying the own deposit; returns the payment page URL",
            description = "May be repeated to get a new link while the payment is pending or after it failed")
    public DepositStartResponse startDeposit(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return depositService.startDeposit(userId, id);
    }

    @GetMapping("/{id}/deposit")
    @Operation(summary = "Own deposit in the pact")
    public DepositResponse deposit(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return depositService.getMyDeposit(userId, id);
    }
}
