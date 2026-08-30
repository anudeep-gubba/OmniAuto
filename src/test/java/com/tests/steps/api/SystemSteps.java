package com.tests.steps.api;

import com.framework.api.ApiResponse;
import com.tests.application.responses.ConfigResponse;
import com.tests.application.responses.HealthResponse;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.SystemApiTestCase;
import com.tests.application.testdata.api.SystemApiTestCase.SystemApiData;
import com.tests.steps.shared.ApiScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertNotNull;

/**
 * Steps behind {@code features/api/system.feature} - a mechanical lift of the old
 * {@code com.tests.tests.api.SystemApiTest} {@code @Test} method bodies into Given/When/Then
 * steps; every service call and assertion is unchanged.
 */
public class SystemSteps {

    private final ApiScenarioContext context;
    private ApiResponse response;

    public SystemSteps(ApiScenarioContext context) {
        this.context = context;
    }

    private static SystemApiData data(String caseName) {
        return TestDataSurface.API.getCaseData(caseName, SystemApiTestCase.class);
    }

    @When("I call GET \\/health")
    public void iCallGetHealth() {
        response = context.systemService.getHealth();
    }

    @Then("the health response should match the {string} system test data's expected status, status, and db status")
    public void theHealthResponseShouldMatch(String caseName) {
        SystemApiData caseData = data(caseName);
        response.assertStatusCode(caseData.expectedStatusCode());
        HealthResponse health = response.as(HealthResponse.class);
        assertEquals(health.status(), caseData.expectedStatus(), "Health check should report overall status 'ok'.");
        assertEquals(health.dbStatus(), caseData.expectedDbStatus(), "Health check should report the database as connected.");
    }

    @And("the health response should include a timestamp")
    public void theHealthResponseShouldIncludeATimestamp() {
        HealthResponse health = response.as(HealthResponse.class);
        assertNotNull(health.timestamp(), "Health check response should include a timestamp.");
    }

    @When("I call GET \\/config")
    public void iCallGetConfig() {
        response = context.systemService.getConfig();
    }

    @Then("the config response should match the {string} system test data's expected status code")
    public void theConfigResponseShouldMatchExpectedStatusCode(String caseName) {
        response.assertStatusCode(data(caseName).expectedStatusCode());
    }

    @And("the config response should deserialize into a non-null config")
    public void theConfigResponseShouldDeserializeIntoANonNullConfig() {
        // Not asserting a specific boolean value for showExploreLinks - that flag is expected to
        // be toggled operationally; the contract this locks in is "the field exists and
        // deserializes", not any particular flag state.
        ConfigResponse config = response.as(ConfigResponse.class);
        assertNotNull(config, "Config response body should deserialize into a non-null ConfigResponse.");
    }
}
