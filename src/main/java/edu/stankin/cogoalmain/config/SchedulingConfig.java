package edu.stankin.cogoalmain.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduled jobs ({@code DeadlineScheduler}). Set {@code app.deadlines.enabled=false}
 * to switch them off, e.g. in tests or on an instance that should only serve requests.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.deadlines", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
