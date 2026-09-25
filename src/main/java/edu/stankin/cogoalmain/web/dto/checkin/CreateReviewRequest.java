package edu.stankin.cogoalmain.web.dto.checkin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.stankin.cogoalmain.db.entity.enums.ReviewStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param comment required when rejecting, so the author knows what to fix
 */
public record CreateReviewRequest(@NotNull ReviewStatus status, @Size(max = 5000) String comment) {

    @JsonIgnore
    @AssertTrue(message = "a comment is required when rejecting")
    public boolean isComment() {
        return status != ReviewStatus.REJECTED || (comment != null && !comment.isBlank());
    }
}
