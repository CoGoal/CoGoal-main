package edu.stankin.cogoalmain.service.event;

import edu.stankin.cogoalmain.db.entity.enums.ParticipantStatus;

/**
 * @param result COMPLETED or FAILED
 */
public record PactResultNotification(String email, String goalTitle, ParticipantStatus result) {
}
