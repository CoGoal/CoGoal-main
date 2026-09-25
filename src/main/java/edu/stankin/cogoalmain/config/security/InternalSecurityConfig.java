package edu.stankin.cogoalmain.config.security;

import edu.stankin.cogoalmain.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Separate security chain for service-to-service endpoints {@code /internal/**}.
 * <p>
 * It is matched before the user chain in {@link SecurityConfig}, so internal requests never pass through
 * the JWT filter and user requests never pass through the internal token filter.
 */
@Configuration
public class InternalSecurityConfig {

    public static final String INTERNAL_PATHS = "/internal/**";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http,
                                                           AppProperties properties,
                                                           RestAuthenticationEntryPoint authenticationEntryPoint,
                                                           RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .securityMatcher(INTERNAL_PATHS)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(InternalTokenFilter.ROLE))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new InternalTokenFilter(properties.internal().token()),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
