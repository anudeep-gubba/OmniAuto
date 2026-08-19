package com.tests.api.responses;

/** Body of {@code GET /health} - unauthenticated liveness/DB-connectivity check. */
public record HealthResponse(String status, String timestamp, String dbStatus) {
}
