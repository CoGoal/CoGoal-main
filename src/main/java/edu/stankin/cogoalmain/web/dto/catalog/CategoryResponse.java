package edu.stankin.cogoalmain.web.dto.catalog;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String description, boolean active) {
}
