package edu.stankin.cogoalmain.client.email;

import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * email-service: sends notification emails. Calls are fire-and-forget.
 */
public interface EmailClient {

    void sendPactInvitation(String email, String inviterUsername, UUID pactId, UUID invitationId);

    /**
     * @param milestoneTitle title of the milestone, or {@code null} when the deadline is the goal's final one
     */
    void sendDeadlineReminder(String email, String goalTitle, String milestoneTitle, Instant deadline);

    /**
     * @param result {@link ParticipantStatus#COMPLETED} or {@link ParticipantStatus#FAILED}
     */
    void sendPactResult(String email, String goalTitle, ParticipantStatus result);
}
