package edu.stankin.cogoalmain.web.dto.goal;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * @param deadline in the future and not later than the goal's deadline
 */
public record CreateMilestoneRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull @Future Instant deadline
) {
}
