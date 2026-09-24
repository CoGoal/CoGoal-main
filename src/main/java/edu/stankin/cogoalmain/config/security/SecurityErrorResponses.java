package edu.stankin.cogoalmain.config.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes JSON error bodies for security failures, which happen before Spring MVC error handling.
 */
final class SecurityErrorResponses {

    private SecurityErrorResponses() {
    }

    // Messages are constants from this package, so no JSON escaping is needed
    static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"status":%d,"error":"%s","message":"%s"}"""
                .formatted(status.value(), status.getReasonPhrase(), message));
    }
}
