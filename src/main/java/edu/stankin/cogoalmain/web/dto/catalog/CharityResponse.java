package edu.stankin.cogoalmain.web.dto.catalog;

import java.util.UUID;

public record CharityResponse(UUID id, String name, String description, String websiteUrl, boolean active) {
}
