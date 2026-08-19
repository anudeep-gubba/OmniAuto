package com.tests.api.responses;

/** Body of {@code GET /config} - unauthenticated public feature flags. */
public record ConfigResponse(boolean showExploreLinks) {
}
