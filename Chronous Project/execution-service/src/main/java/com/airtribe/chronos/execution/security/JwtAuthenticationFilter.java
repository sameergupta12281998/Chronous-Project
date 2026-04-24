package com.airtribe.chronos.execution.security;

import com.airtribe.chronos.commons.security.AuthenticatedUser;
import com.airtribe.chronos.commons.security.GatewayHeaders;
import com.airtribe.chronos.commons.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Validates JWT or trusts gateway-injected identity headers and attaches an
 * AuthenticatedUser principal to the SecurityContext.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwt;
    public JwtAuthenticationFilter(JwtTokenService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            AuthenticatedUser u = resolve(req);
            if (u != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(u, "N/A", List.of()));
            }
            chain.doFilter(req, res);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AuthenticatedUser resolve(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims c = jwt.parse(auth.substring(7));
                return new AuthenticatedUser(UUID.fromString(c.getSubject()),
                        c.get("username", String.class));
            } catch (Exception ignored) { return null; }
        }
        String userId = req.getHeader(GatewayHeaders.USER_ID);
        String username = req.getHeader(GatewayHeaders.USERNAME);
        if (userId != null && !userId.isBlank()) {
            return new AuthenticatedUser(UUID.fromString(userId), username);
        }
        return null;
    }
}
