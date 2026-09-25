package edu.stankin.cogoalmain.exception;

/**
 * The current user may not access this resource (someone else's goal, not a pact participant, ...).
 * Mapped to 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
