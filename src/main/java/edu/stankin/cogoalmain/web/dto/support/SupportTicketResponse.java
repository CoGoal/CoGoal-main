package edu.stankin.cogoalmain.web.dto.support;

import edu.stankin.cogoalmain.db.entity.enums.SupportTicketStatus;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketResponse(
        UUID id,
        String subject,
        String description,
        SupportTicketStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
