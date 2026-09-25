package edu.stankin.cogoalmain.web.dto.support;

import edu.stankin.cogoalmain.db.entity.enums.SupportTicketStatus;
import edu.stankin.cogoalmain.web.dto.goal.UserRef;

import java.time.Instant;
import java.util.UUID;

/**
 * Ticket as admins see it, with its author.
 */
public record AdminSupportTicketResponse(
        UUID id,
        UserRef user,
        String userEmail,
        String subject,
        String description,
        SupportTicketStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
