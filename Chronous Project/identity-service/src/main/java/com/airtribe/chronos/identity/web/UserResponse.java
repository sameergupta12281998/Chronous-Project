package com.airtribe.chronos.identity.web;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, String email, Instant createdAt) {
}
