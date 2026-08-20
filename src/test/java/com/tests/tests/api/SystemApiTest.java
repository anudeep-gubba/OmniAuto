package com.tests.tests.api;

import com.framework.api.ApiResponse;
import com.tests.application.responses.ConfigResponse;
import com.tests.application.responses.HealthResponse;
import com.tests.application.services.SystemService;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.SystemApiTestCase.SystemApiData;
import com.tests.application.testdata.api.SystemApiTestCase;
import org.testng.annotations.Test;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertNotNull;

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
    @Test(groups = {"smoke", "sanity", "api", "system", "positive"})
    public void healthCheckReportsOkWithAConnectedDatabase() {
        SystemApiData data = TestDataSurface.API.getCaseData("healthCheck", SystemApiTestCase.class);
        ApiResponse response = systemService.getHealth();

        response.assertStatusCode(data.expectedStatusCode());
        HealthResponse health = response.as(HealthResponse.class);
        assertEquals(health.status(), data.expectedStatus(), "Health check should report overall status 'ok'.");
        assertEquals(health.dbStatus(), data.expectedDbStatus(), "Health check should report the database as connected.");
        assertNotNull(health.timestamp(), "Health check response should include a timestamp.");
    }

    @Test(groups = {"api", "system", "positive"})
    public void configReturnsThePublicFeatureFlags() {
        SystemApiData data = TestDataSurface.API.getCaseData("systemConfig", SystemApiTestCase.class);
        ApiResponse response = systemService.getConfig();

        response.assertStatusCode(data.expectedStatusCode());
        // Not asserting a specific boolean value for showExploreLinks - that flag is expected to
        // be toggled operationally; the contract this test locks in is "the field exists and
        // deserializes", not any particular flag state.
        ConfigResponse config = response.as(ConfigResponse.class);
        assertNotNull(config, "Config response body should deserialize into a non-null ConfigResponse.");
    }
}
