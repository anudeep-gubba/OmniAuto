package com.tests.tests.api;

import com.framework.api.ApiResponse;
import com.tests.application.responses.ConfigResponse;
import com.tests.application.responses.HealthResponse;
import com.tests.application.services.SystemService;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Coverage of eventhub's two unauthenticated endpoints, {@code GET /health} and
 * {@code GET /config}. Both are public by design (no bearer token, no per-account isolation),
 * so there is no meaningful "negative" case beyond confirming they stay reachable and shaped
 * as documented - unlike every other endpoint in this suite, there is nothing to log in as or
 * clean up afterward.
 */
public class SystemApiTest {

    private final SystemService systemService = new SystemService();

    // Also "sanity": the narrowest "is the app fundamentally alive" checkpoint - one
    // representative live test per surface, distinct from "smoke". See README.md. The API
    // surface's sole sanity test (not AuthApiTest's login, despite also being smoke+sanity-shaped) -
    // /health needs neither auth nor test data to succeed, which is exactly what "is anything
    // even up" should mean, one level narrower than a case that also exercises login itself.
    @Test(groups = {"smoke", "sanity", "api"})
    public void healthCheckReportsOkWithAConnectedDatabase() {
        ApiResponse response = systemService.getHealth();

        response.assertStatusCode(200);
        HealthResponse health = response.as(HealthResponse.class);
        assertEquals(health.status(), "ok");
        assertEquals(health.dbStatus(), "connected");
        assertNotNull(health.timestamp());
    }

    @Test(groups = "api")
    public void configReturnsThePublicFeatureFlags() {
        ApiResponse response = systemService.getConfig();

        response.assertStatusCode(200);
        // Not asserting a specific boolean value for showExploreLinks - that flag is expected to
        // be toggled operationally; the contract this test locks in is "the field exists and
        // deserializes", not any particular flag state.
        ConfigResponse config = response.as(ConfigResponse.class);
        assertNotNull(config);
    }
}
