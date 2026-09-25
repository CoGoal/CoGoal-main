package edu.stankin.cogoalmain.web.controllers.admin;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

final class AdminLocations {

    private AdminLocations() {
    }

    /** {@code Location} of a resource created by POST to the current collection URL. */
    static URI of(UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
    }
}
