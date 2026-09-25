package edu.stankin.cogoalmain.db.entity;

import org.hibernate.Hibernate;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity-based equals/hashCode shared by all entities.
 * <p>
 * Two entities are equal when they are of the same (unproxied) class and have the same non-null id.
 * hashCode is constant per class so it does not change when the id is assigned on persist.
 */
public abstract class BaseEntity {

    public abstract UUID getId();

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        UUID id = getId();
        return id != null && Objects.equals(id, ((BaseEntity) o).getId());
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }

    @Override
    public String toString() {
        return Hibernate.getClass(this).getSimpleName() + "(id=" + getId() + ")";
    }
}
