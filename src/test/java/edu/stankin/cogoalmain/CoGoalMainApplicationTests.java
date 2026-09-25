package edu.stankin.cogoalmain;

import edu.stankin.cogoalmain.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the full context against a real PostgreSQL: Liquibase applies all changesets
 * and Hibernate validates every entity against the resulting schema ({@code ddl-auto=validate}).
 */
class CoGoalMainApplicationTests extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndSchemaMatchesEntities() {
        // Context startup itself fails if Liquibase or schema validation fails
    }

    @Test
    void partialUniqueIndexesAreCreated() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexdef from pg_indexes where indexname in (?, ?) order by indexname",
                String.class, "uq_pact_invitation_pending", "uq_pact_participant_goal_active");

        assertThat(indexes).hasSize(2);
        assertThat(indexes.get(0)).contains("WHERE", "PENDING");
        assertThat(indexes.get(1)).contains("WHERE", "LEFT");
    }

    @Test
    void usersIdHasNoDefault() {
        String columnDefault = jdbcTemplate.queryForObject(
                "select column_default from information_schema.columns where table_name = 'users' and column_name = 'id'",
                String.class);

        assertThat(columnDefault).isNull();
    }
}
