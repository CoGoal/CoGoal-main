package edu.stankin.cogoalmain.web.dto.catalog;

/**
 * Result of an admin delete: rows that other data refers to are deactivated instead of removed.
 */
public record DeletionResponse(Outcome outcome) {

    public enum Outcome {
        /** Nothing referred to the row; it was removed. */
        DELETED,
        /** The row is referenced (goals, pacts, purchases); it was kept with {@code is_active = false}. */
        DEACTIVATED
    }

    public static DeletionResponse deleted() {
        return new DeletionResponse(Outcome.DELETED);
    }

    public static DeletionResponse deactivated() {
        return new DeletionResponse(Outcome.DEACTIVATED);
    }
}
