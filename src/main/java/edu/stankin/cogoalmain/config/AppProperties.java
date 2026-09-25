package edu.stankin.cogoalmain.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Application settings under the {@code app} prefix.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Coins coins,
        @Valid @NotNull Deadlines deadlines,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Internal internal
) {

    /**
     * @param goalCompleted coins credited for a goal completed on time
     */
    public record Coins(@Positive int goalCompleted) {
    }

    /**
     * @param enabled              whether the scheduled jobs run at all (off in tests)
     * @param checkInterval        how often deadlines and reminders are checked
     * @param reminderBefore       how long before a deadline the reminder is sent
     * @param paymentRetryInterval how often charge/release requests that got no answer from payment-service
     *                             are sent again
     */
    public record Deadlines(
            @DefaultValue("true") boolean enabled,
            @NotNull Duration checkInterval,
            @NotNull Duration reminderBefore,
            @NotNull Duration paymentRetryInterval
    ) {
    }

    /**
     * @param path        directory where uploaded proof files are stored
     * @param maxFileSize maximum size of one uploaded file
     */
    public record Storage(@NotNull Path path, @NotNull DataSize maxFileSize) {
    }

    /**
     * @param token shared secret other services send in the {@code X-Internal-Token} header
     */
    public record Internal(@NotBlank @Size(min = 16) String token) {
    }
}
