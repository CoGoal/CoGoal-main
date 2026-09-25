package edu.stankin.cogoalmain.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for tests against a real PostgreSQL: the full application context with Liquibase and
 * {@code ddl-auto=validate}. The database is either
 * <ul>
 *     <li>external: set {@code IT_DB_URL}, {@code IT_DB_USERNAME}, {@code IT_DB_PASSWORD} (an empty database;
 *     Liquibase creates the schema, and test data uses random names so reruns do not collide), or</li>
 *     <li>a PostgreSQL container started with Testcontainers when Docker is available.</li>
 * </ul>
 * Without either the tests are skipped (see {@link DatabaseAvailableCondition}).
 */
@ExtendWith(DatabaseAvailableCondition.class)
@SpringBootTest(properties = {
        "security.jwt.secret=" + TestTokens.SECRET,
        "security.jwt.issuer=" + TestTokens.ISSUER,
        "security.jwt.audience=" + TestTokens.AUDIENCE,
        "app.internal.token=" + TestTokens.INTERNAL_TOKEN,
        "app.deadlines.enabled=false",
        "app.storage.path=target/test-storage"
})
public abstract class PostgresIntegrationTest {

    static final String EXTERNAL_URL = System.getenv("IT_DB_URL");

    private static PostgreSQLContainer container;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv("IT_DB_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("IT_DB_PASSWORD"));
            return;
        }
        PostgreSQLContainer postgres = startedContainer();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // One container for all integration test classes; Testcontainers removes it when the JVM exits
    private static synchronized PostgreSQLContainer startedContainer() {
        if (container == null) {
            container = new PostgreSQLContainer("postgres:16-alpine");
            container.start();
        }
        return container;
    }
}
