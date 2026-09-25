package edu.stankin.cogoalmain.web.controllers;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import edu.stankin.cogoalmain.service.GoalService;
import edu.stankin.cogoalmain.web.dto.goal.MilestoneResponse;
import edu.stankin.cogoalmain.web.dto.goal.UpdateMilestoneRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/milestones")
@RequiredArgsConstructor
@Tag(name = "Goals")
public class MilestoneController {

    private final GoalService goalService;

    @PatchMapping("/{id}")
    @Operation(summary = "Change a milestone of a DRAFT goal; omitted fields stay unchanged")
    public MilestoneResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
                                    @Valid @RequestBody UpdateMilestoneRequest request) {
        return goalService.updateMilestone(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a milestone of a DRAFT goal")
    public void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        goalService.deleteMilestone(userId, id);
    }
}
