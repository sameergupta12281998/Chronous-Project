package com.airtribe.chronos.commons.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "this-is-a-test-secret-that-is-long-enough-12345";

    @Test
    void issuedTokenIsParseableAndCarriesClaims() {
        JwtTokenService svc = new JwtTokenService(SECRET, "chronos-test", 3600);
        UUID userId = UUID.randomUUID();

        String token = svc.issueToken(userId, "alice");
        Claims claims = svc.parse(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("username", String.class)).isEqualTo("alice");
        assertThat(claims.getIssuer()).isEqualTo("chronos-test");
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtTokenService svc = new JwtTokenService(SECRET, "chronos-test", 3600);
        String tampered = svc.issueToken(UUID.randomUUID(), "alice") + "x";

        assertThatThrownBy(() -> svc.parse(tampered))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    void weakSecretIsRejected() {
        assertThatThrownBy(() -> new JwtTokenService("short", "chronos-test", 60))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
