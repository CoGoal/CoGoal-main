package edu.stankin.cogoalmain.config.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single way to find out who makes the current request.
 * <p>
 * In controllers prefer the {@link CurrentUserId @CurrentUserId} parameter; inject this component
 * where there is no controller parameter to use. The id is taken from the JWT ({@code sub}) without
 * touching the database, so it is available even before the profile exists in {@code users}.
 */
@Component
public class CurrentUser {

    public UUID id() {
        return principal().id();
    }

    public AuthenticatedUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            // Only reachable on endpoints that are not protected by the JWT filter
            throw new AuthenticationCredentialsNotFoundException("No authenticated user in the security context");
        }
        return user;
    }
}
