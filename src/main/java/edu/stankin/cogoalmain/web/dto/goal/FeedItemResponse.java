package edu.stankin.cogoalmain.web.dto.goal;

import java.util.UUID;

/**
 * Another user's goal in a pact that is still recruiting.
 *
 * @param pactId the pact to join ({@code POST /pacts/{pactId}/join})
 */
public record FeedItemResponse(GoalSummaryResponse goal, UserRef owner, UUID pactId) {
}
