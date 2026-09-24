package edu.stankin.cogoalmain.config.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for validating incoming bearer tokens.
 *
 * @param secret    shared HMAC secret the auth service signs tokens with (HS256, at least 32 bytes)
 * @param issuer    expected value of the {@code iss} claim
 * @param audience  value that must be present in the {@code aud} claim
 * @param clockSkew tolerated clock difference when checking {@code exp} and {@code iat}
 */
@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull @DefaultValue("30s") Duration clockSkew
) {
}
