package edu.stankin.cogoalmain.web.dto.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotBlank @Size(max = 200) String subject,
        @Size(max = 10000) String description
) {
}
