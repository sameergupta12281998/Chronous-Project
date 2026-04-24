package com.airtribe.chronos.commons.security;

import java.util.UUID;

/**
 * Authenticated principal extracted from a validated JWT.
 */
public record AuthenticatedUser(UUID userId, String username) {
}
