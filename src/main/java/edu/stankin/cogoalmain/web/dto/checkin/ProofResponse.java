package edu.stankin.cogoalmain.web.dto.checkin;

import edu.stankin.cogoalmain.db.entity.enums.ProofType;

import java.time.Instant;
import java.util.UUID;

/**
 * @param downloadUrl API path of the uploaded file (FILE and PHOTO), {@code null} for links
 * @param externalUrl the link (LINK), {@code null} for files
 */
public record ProofResponse(
        UUID id,
        ProofType type,
        String downloadUrl,
        String externalUrl,
        String description,
        Instant uploadedAt
) {
}
