package edu.stankin.cogoalmain.config.security;

import edu.stankin.cogoalmain.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads {@code Authorization: Bearer <token>}, validates the token and puts an
 * authenticated {@link AuthenticatedUser} into the security context.
 * <p>
 * Requests without the header pass through unauthenticated, so public endpoints keep working
 * and protected ones are rejected later by the authorization rules.
 * A present but invalid token is rejected immediately with 401.
 * <p>
 * Intentionally not a {@code @Component}: otherwise Spring Boot would also register it
 * as a plain servlet filter outside the security chain.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtTokenService jwtTokenService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        AuthenticatedUser user;
        try {
            user = jwtTokenService.parse(token);
        } catch (JwtException e) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Invalid bearer token", e));
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(user, token, toAuthorities(user));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private static List<GrantedAuthority> toAuthorities(AuthenticatedUser user) {
        return user.roles().stream()
                .map(role -> role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role.toUpperCase())
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
