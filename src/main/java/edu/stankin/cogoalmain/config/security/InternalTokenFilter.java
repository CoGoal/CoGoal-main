package edu.stankin.cogoalmain.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates service-to-service calls to {@code /internal/**} by the {@code X-Internal-Token} header.
 * <p>
 * Runs only in the internal security chain, so a user's bearer token is never accepted there.
 * Not a {@code @Component} for the same reason as {@link JwtAuthenticationFilter}.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    public static final String ROLE = "INTERNAL";

    private static final String PRINCIPAL = "internal-service";

    private final byte[] expectedToken;

    public InternalTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        // Constant-time comparison so the token cannot be guessed byte by byte from response times
        if (token == null || !MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.clearContext();
            SecurityErrorResponses.write(response, HttpStatus.UNAUTHORIZED,
                    token == null ? "Internal token is required" : "Invalid internal token");
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                PRINCIPAL, null, AuthorityUtils.createAuthorityList("ROLE_" + ROLE)));
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }
}
