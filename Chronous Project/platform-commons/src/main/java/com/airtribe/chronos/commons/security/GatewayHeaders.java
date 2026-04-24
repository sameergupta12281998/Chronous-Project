package com.airtribe.chronos.commons.security;

import java.util.UUID;

/**
 * Constants for headers passed downstream by the API gateway after JWT validation.
 * Downstream services trust these only because the gateway is the single ingress and
 * upstream traffic is protected by the deployment topology.
 */
public final class GatewayHeaders {
    public static final String USER_ID = "X-Chronos-User-Id";
    public static final String USERNAME = "X-Chronos-Username";

    private GatewayHeaders() {
    }

    public static UUID parseUserId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return UUID.fromString(header);
    }
}
