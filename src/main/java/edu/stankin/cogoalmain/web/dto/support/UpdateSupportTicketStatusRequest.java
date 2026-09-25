package edu.stankin.cogoalmain.web.dto.support;

import edu.stankin.cogoalmain.db.entity.enums.SupportTicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSupportTicketStatusRequest(@NotNull SupportTicketStatus status) {
}
