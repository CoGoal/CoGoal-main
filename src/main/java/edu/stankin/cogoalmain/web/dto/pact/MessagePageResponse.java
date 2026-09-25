package edu.stankin.cogoalmain.web.dto.pact;

import java.time.Instant;
import java.util.List;

/**
 * Chat messages, newest first.
 *
 * @param nextBefore pass as {@code before} to load older messages; {@code null} when there are no more
 */
public record MessagePageResponse(List<MessageResponse> messages, Instant nextBefore) {
}
