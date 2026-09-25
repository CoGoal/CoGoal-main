package edu.stankin.cogoalmain.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    /** Injected wherever "now" matters (deadlines, statuses), so tests can fix the time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
