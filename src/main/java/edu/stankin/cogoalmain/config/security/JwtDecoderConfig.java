package edu.stankin.cogoalmain.config.security;

import edu.stankin.cogoalmain.service.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey key = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(tokenValidator(properties));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> tokenValidator(JwtProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(properties.clockSkew()),
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(properties.audience())),
                required(JwtClaimNames.SUB),
                required(JwtClaimNames.EXP),
                required(JwtClaimNames.IAT),
                required(JwtTokenService.ROLES_CLAIM)
        );
    }

    private static OAuth2TokenValidator<Jwt> required(String claim) {
        return new JwtClaimValidator<>(claim, Objects::nonNull);
    }
}
