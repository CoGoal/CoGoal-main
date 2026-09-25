package edu.stankin.cogoalmain.support;

import edu.stankin.cogoalmain.db.entity.GeneratedIdEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

/**
 * Helpers for building entities in unit tests without a database.
 */
public final class TestEntities {

    private TestEntities() {
    }

    /** Sets the id that Hibernate would generate on persist. */
    public static <T extends GeneratedIdEntity> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static <T extends GeneratedIdEntity> T withId(T entity) {
        return withId(entity, UUID.randomUUID());
    }
}
