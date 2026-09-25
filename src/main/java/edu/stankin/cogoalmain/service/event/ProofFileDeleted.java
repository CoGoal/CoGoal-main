package edu.stankin.cogoalmain.service.event;

/**
 * A proof with an uploaded file was deleted; the file itself is removed once that is committed.
 */
public record ProofFileDeleted(String fileKey) {
}
