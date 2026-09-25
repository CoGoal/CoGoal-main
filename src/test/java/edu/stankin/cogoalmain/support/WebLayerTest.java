package edu.stankin.cogoalmain.support;

import edu.stankin.cogoalmain.config.AppConfig;
import edu.stankin.cogoalmain.config.security.CurrentUser;
import edu.stankin.cogoalmain.config.security.InternalSecurityConfig;
import edu.stankin.cogoalmain.config.security.JwtDecoderConfig;
import edu.stankin.cogoalmain.config.security.RestAccessDeniedHandler;
import edu.stankin.cogoalmain.config.security.RestAuthenticationEntryPoint;
import edu.stankin.cogoalmain.config.security.SecurityConfig;
import edu.stankin.cogoalmain.service.JwtTokenService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @WebMvcTest} slice with the real security setup: both filter chains, JWT validation
 * and the error handlers. Requests carry tokens from {@link TestTokens}; services are {@code @MockitoBean}s.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest(properties = {
        "security.jwt.secret=" + TestTokens.SECRET,
        "security.jwt.issuer=" + TestTokens.ISSUER,
        "security.jwt.audience=" + TestTokens.AUDIENCE,
        "app.internal.token=" + TestTokens.INTERNAL_TOKEN
})
@Import({
        SecurityConfig.class, InternalSecurityConfig.class, JwtDecoderConfig.class, JwtTokenService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, CurrentUser.class, AppConfig.class
})
public @interface WebLayerTest {

    /** Controllers under test. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
