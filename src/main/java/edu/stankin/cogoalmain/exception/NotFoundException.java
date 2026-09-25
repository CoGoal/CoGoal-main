package edu.stankin.cogoalmain.exception;

/**
 * Requested resource does not exist. Mapped to 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " " + id + " not found");
    }
}
