package edu.stankin.cogoalmain.service.event;

import java.time.Instant;

/**
 * @param milestoneTitle {@code null} when the reminder is about the goal's final deadline
 */
public record DeadlineReminderNotification(String email, String goalTitle, String milestoneTitle, Instant deadline) {
}
