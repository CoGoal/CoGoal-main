package edu.stankin.cogoalmain.web.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Admin create/replace of a charity.
 *
 * @param active {@code null} keeps the current value (new charities are active)
 */
public record CharityRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @URL @Size(max = 500) String websiteUrl,
        Boolean active
) {
}
