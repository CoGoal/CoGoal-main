package edu.stankin.cogoalmain.exception;

/**
 * The operation conflicts with a business rule or the current status of a resource. Mapped to 409.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
