package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.config.security.AuthenticatedUser;
import edu.stankin.cogoalmain.config.security.JwtDecoderConfig;
import edu.stankin.cogoalmain.config.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private static final String OTHER_SECRET = "other-secret-other-secret-other-secret-1";
    private static final JwtProperties PROPERTIES =
            new JwtProperties(SECRET, "cogoal-auth", "cogoal-main", Duration.ofSeconds(30));

    private final JwtTokenService service =
            new JwtTokenService(new JwtDecoderConfig().jwtDecoder(PROPERTIES));

    private final UUID userId = UUID.randomUUID();

    @Test
    void parsesValidToken() {
        AuthenticatedUser user = service.parse(token(SECRET, claims -> { }));

        assertThat(user.id()).isEqualTo(userId);
        assertThat(user.issuer()).isEqualTo("cogoal-auth");
        assertThat(user.audience()).containsExactly("cogoal-main");
        assertThat(user.roles()).isEqualTo(Set.of("USER", "ADMIN"));
        assertThat(user.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void rejectsWrongSignature() {
        assertInvalid(token(OTHER_SECRET, claims -> { }));
    }

    @Test
    void rejectsExpiredToken() {
        Instant past = Instant.now().minus(Duration.ofHours(2));
        assertInvalid(token(SECRET, claims -> claims.issuedAt(past).expiresAt(past.plusSeconds(60))));
    }

    @Test
    void rejectsWrongIssuer() {
        assertInvalid(token(SECRET, claims -> claims.issuer("someone-else")));
    }

    @Test
    void rejectsWrongAudience() {
        assertInvalid(token(SECRET, claims -> claims.audience(List.of("other-service"))));
    }

    @Test
    void rejectsMissingRoles() {
        assertInvalid(token(SECRET, claims -> claims.claims(c -> c.remove(JwtTokenService.ROLES_CLAIM))));
    }

    @Test
    void rejectsNonUuidSubject() {
        assertInvalid(token(SECRET, claims -> claims.subject("not-a-uuid")));
    }

    @Test
    void rejectsGarbage() {
        assertInvalid("not.a.jwt");
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    private String token(String secret, Consumer<JwtClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("cogoal-auth")
                .subject(userId.toString())
                .audience(List.of("cogoal-main"))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .claim(JwtTokenService.ROLES_CLAIM, List.of("USER", "ADMIN"));
        customizer.accept(claims);

        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
