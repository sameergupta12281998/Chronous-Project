package com.airtribe.chronos.identity.web;

import java.util.UUID;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String username
) {
}
