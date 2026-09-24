package edu.stankin.cogoalmain.config.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Principal built from a validated bearer token.
 * Inject into controllers with {@code @AuthenticationPrincipal AuthenticatedUser user}.
 *
 * @param id        user id from the {@code sub} claim
 * @param issuer    {@code iss} claim
 * @param audience  {@code aud} claim
 * @param roles     {@code roles} claim as sent by the issuer (without the {@code ROLE_} prefix)
 * @param issuedAt  {@code iat} claim
 * @param expiresAt {@code exp} claim
 */
public record AuthenticatedUser(
        UUID id,
        String issuer,
        List<String> audience,
        Set<String> roles,
        Instant issuedAt,
        Instant expiresAt
) {
}
