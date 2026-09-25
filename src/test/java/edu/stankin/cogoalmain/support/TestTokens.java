package edu.stankin.cogoalmain.support;

import edu.stankin.cogoalmain.service.JwtTokenService;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues HS256 tokens the way auth-service does, for tests that go through the security filter.
 * Values match {@link #PROPERTIES}, which tests pass to the Spring context.
 */
public final class TestTokens {

    public static final String SECRET = "test-secret-test-secret-test-secret-123";
    public static final String ISSUER = "pact-auth";
    public static final String AUDIENCE = "pact-main";
    public static final String INTERNAL_TOKEN = "test-internal-token-123456";

    /** Properties for {@code @SpringBootTest}/{@code @WebMvcTest} that make the context accept these tokens. */
    public static final String[] PROPERTIES = {
            "security.jwt.secret=" + SECRET,
            "security.jwt.issuer=" + ISSUER,
            "security.jwt.audience=" + AUDIENCE,
            "app.internal.token=" + INTERNAL_TOKEN
    };

    private TestTokens() {
    }

    public static String user(UUID userId) {
        return token(userId, List.of("USER"));
    }

    public static String admin(UUID userId) {
        return token(userId, List.of("USER", "ADMIN"));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static String token(UUID userId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim(JwtTokenService.ROLES_CLAIM, roles)
                .build();

        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
