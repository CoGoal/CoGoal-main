package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.service.CheckInService;
import edu.stankin.cogoalmain.service.ReviewService;
import edu.stankin.cogoalmain.web.dto.checkin.CheckInResponse;
import edu.stankin.cogoalmain.web.dto.checkin.CheckInSummaryResponse;
import edu.stankin.cogoalmain.web.dto.checkin.CreateCheckInRequest;
import edu.stankin.cogoalmain.web.dto.checkin.CreateReviewRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Reports on milestones and goals, and their review by partners")
public class CheckInController {

    private final CheckInService checkInService;
    private final ReviewService reviewService;

    @PostMapping("/pacts/{id}/check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report on a milestone of the own goal, or on the whole goal (milestoneId = null)",
            description = "Only in an active pact, before the deadline. The final report needs all milestones "
                    + "completed. Add evidence with POST /check-ins/{id}/proofs")
    public CheckInResponse create(@CurrentUserId UUID userId, @PathVariable UUID id,
                                  @Valid @RequestBody CreateCheckInRequest request) {
        return checkInService.create(userId, id, request);
    }

    @GetMapping("/pacts/{id}/check-ins")
    @Operation(summary = "Reports in the pact, newest first")
    public Page<CheckInSummaryResponse> listForPact(
            @CurrentUserId UUID userId, @PathVariable UUID id,
            @ParameterObject @SortDefault(sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return checkInService.listForPact(userId, id, pageable);
    }

    @GetMapping("/check-ins/to-review")
    @Operation(summary = "Partners' pending reports the user has not reviewed yet, oldest first")
    public Page<CheckInSummaryResponse> toReview(
            @CurrentUserId UUID userId,
            @ParameterObject @SortDefault(sort = "submittedAt") Pageable pageable) {
        return checkInService.toReview(userId, pageable);
    }

    @GetMapping("/check-ins/{id}")
    @Operation(summary = "Report with its proofs and reviews")
    public CheckInResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return checkInService.get(userId, id);
    }

    @PostMapping("/check-ins/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Approve or reject a partner's pending report (a comment is required to reject)",
            description = "The first review decides; an approved milestone report completes the milestone")
    public CheckInResponse review(@CurrentUserId UUID userId, @PathVariable UUID id,
                                  @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.review(userId, id, request);
    }
}
