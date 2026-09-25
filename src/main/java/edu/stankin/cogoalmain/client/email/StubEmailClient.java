package edu.stankin.cogoalmain.client.email;

import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Placeholder until email-service exists: only logs what would be sent.
 */
@Slf4j
@Component
public class StubEmailClient implements EmailClient {

    @Override
    public void sendPactInvitation(String email, String inviterUsername, UUID pactId, UUID invitationId) {
        log.info("[email stub] pact invitation to {}: from={}, pact={}, invitation={}",
                email, inviterUsername, pactId, invitationId);
    }

    @Override
    public void sendDeadlineReminder(String email, String goalTitle, String milestoneTitle, Instant deadline) {
        log.info("[email stub] deadline reminder to {}: goal='{}', milestone='{}', deadline={}",
                email, goalTitle, milestoneTitle, deadline);
    }

    @Override
    public void sendPactResult(String email, String goalTitle, ParticipantStatus result) {
        log.info("[email stub] pact result to {}: goal='{}', result={}", email, goalTitle, result);
    }
}
