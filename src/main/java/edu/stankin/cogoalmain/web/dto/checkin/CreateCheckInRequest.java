package edu.stankin.cogoalmain.web.dto.checkin;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param milestoneId a PENDING milestone of the author's own goal, or {@code null} for the final report
 *                    on the whole goal (allowed once all milestones are completed)
 * @param comment     what was done, in the author's words
 */
public record CreateCheckInRequest(UUID milestoneId, @Size(max = 5000) String comment) {
}
