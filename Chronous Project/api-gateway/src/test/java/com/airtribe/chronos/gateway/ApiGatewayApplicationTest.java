package com.airtribe.chronos.gateway;

import com.airtribe.chronos.commons.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiGatewayApplicationTest {

    @LocalServerPort int port;
    @Autowired JwtTokenService jwt;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void healthEndpointIsPublic() {
        client().get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void protectedRouteWithoutTokenIs401() {
        client().get().uri("/api/v1/jobs/anything").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void protectedRouteWithInvalidTokenIs401() {
        client().get().uri("/api/v1/jobs/anything")
                .header("Authorization", "Bearer not-a-real-jwt")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void authPathBypassesJwtFilter() {
        // /api/v1/auth/** is bypassed by the JWT filter; with no upstream the gateway will fail to connect (5xx),
        // but the auth filter must not reject it as 401.
        client().get().uri("/api/v1/auth/login").exchange()
                .expectStatus().value(s -> {
                    if (s == 401) throw new AssertionError("auth path must not be 401-blocked");
                });
    }

    @Test
    void validTokenPassesAuthFilter() {
        // With valid token, request reaches the upstream stage. No upstream up in test → 5xx, NOT 401.
        String token = "Bearer " + jwt.issueToken(UUID.randomUUID(), "alice");
        client().get().uri("/api/v1/jobs/anything")
                .header("Authorization", token)
                .exchange()
                .expectStatus().value(s -> {
                    if (s == 401) throw new AssertionError("valid token should not be rejected by auth filter");
                });
    }
}
