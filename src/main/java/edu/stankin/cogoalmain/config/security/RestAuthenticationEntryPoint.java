package edu.stankin.cogoalmain.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Responds with 401 and a JSON body when the request has no valid bearer token.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean tokenPresent = request.getHeader(HttpHeaders.AUTHORIZATION) != null;

        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                tokenPresent ? "Bearer error=\"invalid_token\"" : "Bearer");
        SecurityErrorResponses.write(response, HttpStatus.UNAUTHORIZED,
                tokenPresent ? "Invalid or expired bearer token" : "Bearer token is required");
    }
}
