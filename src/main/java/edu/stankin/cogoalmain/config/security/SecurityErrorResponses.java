package edu.stankin.cogoalmain.config.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes RFC 9457 problem details for security failures, which happen before Spring MVC error handling.
 * The body has the same shape as the {@code ProblemDetail} responses of the controllers.
 */
final class SecurityErrorResponses {

    private SecurityErrorResponses() {
    }

    // Details are constants from this package, so no JSON escaping is needed
    static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
