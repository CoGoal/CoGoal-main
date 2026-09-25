package edu.stankin.cogoalmain;

import edu.stankin.cogoalmain.support.TestTokens;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application without a database: Hibernate builds the entity model and Spring Data
 * creates every repository, which parses all {@code @Query} HQL and derived query names against it.
 * Catches wrong property names, broken mappings and HQL syntax errors without Docker.
 * <p>
 * It does NOT check the entities against the real schema; {@link CoGoalMainApplicationTests} does that.
 */
@SpringBootTest(properties = {
        "security.jwt.secret=" + TestTokens.SECRET,
        "app.internal.token=" + TestTokens.INTERNAL_TOKEN,
        "app.deadlines.enabled=false",
        "app.storage.path=target/test-storage",
        // Nothing listens here; nothing must try to connect during startup
        "spring.datasource.url=jdbc:postgresql://localhost:1/none",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false"
})
class JpaModelAndQueriesTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void entityModelAndRepositoryQueriesAreValid() {
        assertThat(entityManagerFactory.getMetamodel().getEntities()).hasSize(16);
    }
}
