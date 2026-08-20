package com.tests.application.responses;

/** Body of {@code GET /config} - unauthenticated public feature flags. */
public record ConfigResponse(boolean showExploreLinks) {
}
