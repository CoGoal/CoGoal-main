package edu.stankin.cogoalmain.exception;

/**
 * A service this one depends on (payment-service, email-service) failed. Mapped to 502.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }
}
