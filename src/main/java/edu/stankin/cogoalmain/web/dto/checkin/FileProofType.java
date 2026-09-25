package edu.stankin.cogoalmain.web.dto.checkin;

import edu.stankin.cogoalmain.db.entity.enums.ProofType;

/**
 * Proof types that come with an uploaded file (LINK proofs are sent as JSON instead).
 */
public enum FileProofType {
    FILE(ProofType.FILE),
    /** Must be an image ({@code image/*} content type). */
    PHOTO(ProofType.PHOTO);

    private final ProofType proofType;

    FileProofType(ProofType proofType) {
        this.proofType = proofType;
    }

    public ProofType proofType() {
        return proofType;
    }
}
