package edu.stankin.cogoalmain.service.scheduler;

import edu.stankin.cogoalmain.client.payment.PaymentClient;
import edu.stankin.cogoalmain.config.AppConfig;
import edu.stankin.cogoalmain.config.SchedulingConfig;
import edu.stankin.cogoalmain.service.DeadlineService;
import edu.stankin.cogoalmain.support.TestTokens;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scheduled jobs are registered with the interval values from application.yml ("5m", "1h"),
 * and switched off by {@code app.deadlines.enabled=false}.
 */
class DeadlineSchedulerConfigTest {

    @Nested
    @SpringBootTest(
            classes = {AppConfig.class, SchedulingConfig.class, DeadlineScheduler.class},
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = "app.internal.token=" + TestTokens.INTERNAL_TOKEN)
    class Enabled {

        @Autowired
        private ApplicationContext context;
        @MockitoBean
        private DeadlineService deadlineService;
        @MockitoBean
        private PaymentClient paymentClient;

        @Test
        void bothJobsAreScheduled() {
            assertThat(context.getBeansOfType(ScheduledTaskHolder.class).values())
                    .flatMap(ScheduledTaskHolder::getScheduledTasks)
                    .hasSize(2);
        }
    }

    @Nested
    @SpringBootTest(
            classes = {AppConfig.class, SchedulingConfig.class, DeadlineScheduler.class},
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {"app.internal.token=" + TestTokens.INTERNAL_TOKEN, "app.deadlines.enabled=false"})
    class Disabled {

        @Autowired
        private ApplicationContext context;
        @MockitoBean
        private DeadlineService deadlineService;
        @MockitoBean
        private PaymentClient paymentClient;

        @Test
        void nothingIsScheduled() {
            assertThat(context.getBeansOfType(ScheduledTaskHolder.class)).isEmpty();
        }
    }
}
