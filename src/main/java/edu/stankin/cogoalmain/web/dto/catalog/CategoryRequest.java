package edu.stankin.cogoalmain.web.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin create/replace of a category.
 *
 * @param active {@code null} keeps the current value (new categories are active)
 */
public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        Boolean active
) {
}
