package com.airtribe.chronos.commons.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Symmetric HS256 JWT helper shared by every service so the Identity service can issue
 * tokens and downstream services can validate them with the same secret.
 *
 * <p>The secret is supplied at construction time via configuration; never hard coded.
 */
public class JwtTokenService {

    private final SecretKey signingKey;
    private final String issuer;
    private final long ttlSeconds;

    public JwtTokenService(String secret, String issuer, long ttlSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public String issueToken(UUID userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            throw new InvalidJwtException("Invalid JWT token", ex);
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
