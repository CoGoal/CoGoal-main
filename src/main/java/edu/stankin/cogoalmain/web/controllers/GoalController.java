package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.db.entity.enums.GoalStatus;
import edu.stankin.cogoalmain.service.GoalService;
import edu.stankin.cogoalmain.web.dto.goal.CreateGoalRequest;
import edu.stankin.cogoalmain.web.dto.goal.CreateMilestoneRequest;
import edu.stankin.cogoalmain.web.dto.goal.FeedItemResponse;
import edu.stankin.cogoalmain.web.dto.goal.GoalResponse;
import edu.stankin.cogoalmain.web.dto.goal.GoalSummaryResponse;
import edu.stankin.cogoalmain.web.dto.goal.MilestoneResponse;
import edu.stankin.cogoalmain.web.dto.goal.UpdateGoalRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
@Tag(name = "Goals", description = "Own goals, their milestones, and the feed of goals looking for partners")
public class GoalController {

    private final GoalService goalService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a goal in DRAFT")
    public GoalResponse create(@CurrentUserId UUID userId, @Valid @RequestBody CreateGoalRequest request) {
        return goalService.create(userId, request);
    }

    @GetMapping("/my")
    @Operation(summary = "Own goals, optionally filtered by status")
    public Page<GoalSummaryResponse> my(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) GoalStatus status,
            @ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return goalService.getMyGoals(userId, status, pageable);
    }

    @GetMapping("/feed")
    @Operation(summary = "Other users' goals in pacts that are still open for joining")
    public Page<FeedItemResponse> feed(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) UUID categoryId,
            @ParameterObject @SortDefault(sort = "goal.deadline") Pageable pageable) {
        return goalService.feed(userId, categoryId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Goal with milestones; someone else's draft is not visible")
    public GoalResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return goalService.get(userId, id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Change a DRAFT goal; omitted fields stay unchanged")
    public GoalResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
                               @Valid @RequestBody UpdateGoalRequest request) {
        return goalService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a DRAFT goal with its milestones")
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        goalService.delete(userId, id);
    }

    @PostMapping("/{id}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a milestone to a DRAFT goal")
    public MilestoneResponse addMilestone(@CurrentUserId UUID userId, @PathVariable UUID id,
                                          @Valid @RequestBody CreateMilestoneRequest request) {
        return goalService.addMilestone(userId, id, request);
    }
}
