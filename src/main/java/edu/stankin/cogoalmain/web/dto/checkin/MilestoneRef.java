package edu.stankin.cogoalmain.web.dto.checkin;

import java.time.Instant;
import java.util.UUID;

public record MilestoneRef(UUID id, String title, Instant deadline) {
}
