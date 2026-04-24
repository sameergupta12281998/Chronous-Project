package com.airtribe.chronos.job.security;

import com.airtribe.chronos.commons.security.AuthenticatedUser;
import com.airtribe.chronos.commons.security.JwtTokenService;
import com.airtribe.chronos.commons.security.GatewayHeaders;
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
 * Validates incoming JWT and/or trusts gateway-injected headers, then attaches an
 * AuthenticatedUser principal to the SecurityContext for downstream handling.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            AuthenticatedUser user = resolveUser(request);
            if (user != null) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, "N/A", List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AuthenticatedUser resolveUser(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims claims = jwtTokenService.parse(auth.substring(7));
                return new AuthenticatedUser(UUID.fromString(claims.getSubject()),
                        claims.get("username", String.class));
            } catch (Exception ignored) {
                return null;
            }
        }
        String userId = request.getHeader(GatewayHeaders.USER_ID);
        String username = request.getHeader(GatewayHeaders.USERNAME);
        if (userId != null && !userId.isBlank()) {
            return new AuthenticatedUser(UUID.fromString(userId), username);
        }
        return null;
    }
}
