package edu.stankin.cogoalmain.config.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a controller method parameter of type {@link java.util.UUID} to the id of the current user
 * (the {@code sub} claim of the bearer token):
 * <pre>{@code
 * @GetMapping("/goals/my")
 * public Page<GoalResponse> my(@CurrentUserId UUID userId, Pageable pageable) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
