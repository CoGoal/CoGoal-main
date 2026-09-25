package edu.stankin.cogoalmain.web.dto.goal;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Partial update of a milestone of a DRAFT goal: {@code null} fields are left unchanged.
 */
public record UpdateMilestoneRequest(
        @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
        String description,
        @Future Instant deadline
) {
}
