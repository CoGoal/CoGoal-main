package edu.stankin.cogoalmain.web.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Proof of type LINK.
 *
 * @param externalUrl http(s) link to the evidence; other schemes (e.g. {@code javascript:}) are rejected
 */
public record LinkProofRequest(
        @NotBlank @URL @Pattern(regexp = "(?i)^https?://.*", message = "must be an http or https link")
        @Size(max = 500) String externalUrl,
        @Size(max = 2000) String description
) {
}
