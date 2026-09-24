package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.config.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validates bearer tokens issued by the auth service and turns them into {@link AuthenticatedUser}.
 * Expected claims: iss, sub, aud, exp, iat, roles.
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    public static final String ROLES_CLAIM = "roles";

    private final JwtDecoder jwtDecoder;

    /**
     * @throws JwtException if the token is malformed, has a bad signature, is expired
     *                      or its claims do not match the expected ones
     */
    public AuthenticatedUser parse(String token) {
        Jwt jwt = jwtDecoder.decode(token);

        return new AuthenticatedUser(
                parseUserId(jwt.getSubject()),
                jwt.getClaimAsString("iss"),
                jwt.getAudience(),
                parseRoles(jwt.getClaim(ROLES_CLAIM)),
                jwt.getIssuedAt(),
                jwt.getExpiresAt()
        );
    }

    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new BadJwtException("Claim 'sub' is not a valid user id", e);
        }
    }

    // Accepts both a JSON array ["USER", "ADMIN"] and a space separated string "USER ADMIN"
    private static Set<String> parseRoles(Object claim) {
        Collection<?> values;
        if (claim instanceof Collection<?> collection) {
            values = collection;
        } else if (claim instanceof String string) {
            values = List.of(string.trim().split("\\s+"));
        } else {
            throw new BadJwtException("Claim 'roles' must be an array of strings");
        }

        Set<String> roles = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String role)) {
                throw new BadJwtException("Claim 'roles' must be an array of strings");
            }
            if (!role.isBlank()) {
                roles.add(role);
            }
        }
        return roles;
    }
}
