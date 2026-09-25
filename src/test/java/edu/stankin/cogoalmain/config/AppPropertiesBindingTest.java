package edu.stankin.cogoalmain.config;

import edu.stankin.cogoalmain.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the {@code app.*} block of application.yml binds to {@link AppProperties} with its defaults.
 */
@SpringBootTest(
        classes = AppConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.internal.token=" + TestTokens.INTERNAL_TOKEN)
class AppPropertiesBindingTest {

    @Autowired
    private AppProperties properties;

    @Test
    void bindsDefaultsFromApplicationYml() {
        assertThat(properties.coins().goalCompleted()).isEqualTo(100);
        assertThat(properties.deadlines().checkInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.deadlines().reminderBefore()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.deadlines().enabled()).isTrue();
        assertThat(properties.deadlines().paymentRetryInterval()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.storage().path().normalize()).isEqualTo(Path.of("data/proofs"));
        assertThat(properties.storage().maxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(properties.internal().token()).isEqualTo(TestTokens.INTERNAL_TOKEN);
    }
}
