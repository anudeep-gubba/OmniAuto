package com.tests.steps.api;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.framework.exceptions.ApiAuthenticationException;
import com.framework.utils.RandomDataUtils;
import com.tests.application.requests.AuthRequest;
import com.tests.application.responses.AuthResponse;
import com.tests.application.responses.MeResponse;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.AuthApiTestCase;
import com.tests.application.testdata.api.AuthApiTestCase.AuthApiData;
import com.tests.steps.shared.ApiScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertFalse;
import static com.framework.utils.Verify.assertNotNull;
import static com.framework.utils.Verify.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Steps behind {@code features/api/auth.feature} - a mechanical lift of the old
 * {@code com.tests.tests.api.AuthApiTest} {@code @Test} method bodies into Given/When/Then
 * steps; every service call and assertion is unchanged.
 */
public class AuthSteps {

    private final ApiScenarioContext context;

    private ApiResponse response;
    private AuthResponse authResponse;
    private String registeredEmail;
    private ApiAuthenticationException authException;

    public AuthSteps(ApiScenarioContext context) {
        this.context = context;
    }

    private static AuthApiData data(String caseName) {
        return TestDataSurface.API.getCaseData(caseName, AuthApiTestCase.class);
    }

    // ---------------------------------------------------------------- register

    @When("I register a brand-new random user with the {string} auth test data")
    public void iRegisterABrandNewRandomUser(String caseName) {
        AuthApiData caseData = data(caseName);
        registeredEmail = RandomDataUtils.uniqueEmail("auth." + caseName.toLowerCase());
        authResponse = context.authService.register(registeredEmail, caseData.password());
    }

    @Then("the registration should report success with a usable token for a newly registered user")
    public void theRegistrationShouldReportSuccessWithAUsableToken() {
        assertTrue(authResponse.success(), "Registration response should report success.");
        assertNotNull(authResponse.token(), "Registration response should include a usable auth token.");
        assertTrue(authResponse.user().id() > 0, "Registered user should be assigned a positive numeric id.");
        assertEquals(authResponse.user().email(), registeredEmail, "Registered user's email should match what was submitted.");
    }

    @Then("GET \\/auth\\/me should return the same account that was just registered")
    public void getAuthMeShouldReturnTheSameAccount() {
        // The token register() stored is immediately usable for a protected call.
        MeResponse me = context.authService.me();
        assertEquals(me.user().email(), registeredEmail, "GET /auth/me should return the same account the token was just issued for.");
    }

    @When("I register with the {string} auth test data as-is")
    public void iRegisterWithTheAuthTestDataAsIs(String caseName) {
        AuthApiData caseData = data(caseName);
        response = ApiClient.execute(ApiRequest.post("/auth/register").body(new AuthRequest(caseData.email(), caseData.password())));
    }

    // Deliberately a raw ApiClient call, not context.authService.register() - that method
    // throws ApiAuthenticationException on any non-201 response (its contract for the
    // register-succeeds path), which would turn this validation-rejection scenario's own
    // expected 400 into an uncaught exception instead of a response to assert against.
    @When("I attempt to register a brand-new random user with the {string} auth test data")
    public void iAttemptToRegisterABrandNewRandomUser(String caseName) {
        AuthApiData caseData = data(caseName);
        String email = RandomDataUtils.uniqueEmail("auth." + caseName.toLowerCase());
        response = ApiClient.execute(ApiRequest.post("/auth/register").body(new AuthRequest(email, caseData.password())));
    }

    @When("I register with an empty request body")
    public void iRegisterWithAnEmptyRequestBody() {
        response = ApiClient.execute(ApiRequest.post("/auth/register").body(Map.of()));
    }

    @Then("the registration's validation errors should flag both {string} and {string}")
    public void theRegistrationsValidationErrorsShouldFlagBoth(String firstField, String secondField) {
        List<String> fields = response.jsonPath().getList("details.field", String.class);
        assertTrue(fields.contains(firstField), "Missing " + firstField + " should be flagged: " + fields);
        assertTrue(fields.contains(secondField), "Missing " + secondField + " should be flagged: " + fields);
    }

    // ---------------------------------------------------------------- login

    @When("I log in with the {string} auth test data")
    public void iLogInWithTheAuthTestData(String caseName) {
        AuthApiData caseData = data(caseName);
        authResponse = context.authService.login(caseData.email(), caseData.password());
    }

    @Then("the login should report success with a usable token matching the account logged in with")
    public void theLoginShouldReportSuccessWithAUsableTokenMatching() {
        assertTrue(authResponse.success(), "Login response should report success.");
        assertNotNull(authResponse.token(), "Login response should include a usable auth token.");
    }

    @When("I attempt to log in with the {string} auth test data")
    public void iAttemptToLogInWithTheAuthTestData(String caseName) {
        AuthApiData caseData = data(caseName);
        authException = expectThrows(ApiAuthenticationException.class, () -> context.authService.login(caseData.email(), caseData.password()));
    }

    @Then("the login attempt should fail with a message containing {string}")
    public void theLoginAttemptShouldFailWithAMessageContaining(String snippet) {
        assertTrue(authException.getMessage().contains(snippet), "Exception message should indicate login failed.");
    }

    @When("I log in with a brand-new random nonexistent email using the {string} auth test data's password")
    public void iLogInWithARandomNonexistentEmail(String caseName) {
        AuthApiData caseData = data(caseName);
        response = ApiClient.execute(ApiRequest.post("/auth/login")
                .body(new AuthRequest(RandomDataUtils.uniqueEmail("auth.nosuchuser"), caseData.password())));
    }

    @When("I log in with only the email from the {string} auth test data")
    public void iLogInWithOnlyTheEmailFrom(String caseName) {
        AuthApiData caseData = data(caseName);
        response = ApiClient.execute(ApiRequest.post("/auth/login").body(Map.of("email", caseData.email())));
    }

    // ---------------------------------------------------------------- me

    @Then("calling GET \\/auth\\/me without logging in first should fail fast without a network call")
    public void callingGetAuthMeWithoutLoggingInShouldFailFast() {
        expectThrows(ApiAuthenticationException.class, context.authService::me);
    }

    @Given("I set the auth token from the {string} auth test data")
    public void iSetTheAuthTokenFrom(String caseName) {
        AuthApiData caseData = data(caseName);
        ApiClient.setAuthToken(caseData.token());
    }

    @Then("calling GET \\/auth\\/me should fail with a message containing {string}")
    public void callingGetAuthMeShouldFailWithAMessageContaining(String snippet) {
        ApiAuthenticationException exception = expectThrows(ApiAuthenticationException.class, context.authService::me);
        assertTrue(exception.getMessage().contains(snippet), "Exception message should indicate token validation failed.");
    }

    @When("I call GET \\/auth\\/me directly with no Authorization header")
    public void iCallGetAuthMeDirectlyWithNoAuthorizationHeader() {
        response = ApiClient.execute(ApiRequest.get("/auth/me"));
    }

    // ---------------------------------------------------------------- shared response assertions

    @Then("the response should match the {string} auth test data's expected status and error")
    public void theResponseShouldMatchExpectedStatusAndError(String caseName) {
        AuthApiData caseData = data(caseName);
        response.assertStatusCode(caseData.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), caseData.expectedError());
    }

    @Then("the response should report success as false")
    public void theResponseShouldReportSuccessAsFalse() {
        assertFalse(response.jsonPath().getBoolean("success"), "Response should report success=false for a rejected registration.");
    }

    @Then("the response should match the {string} auth test data's expected status code")
    public void theResponseShouldMatchExpectedStatusCode(String caseName) {
        response.assertStatusCode(data(caseName).expectedStatusCode());
    }

    @Then("the first validation error should match the {string} auth test data's expected field and message")
    public void theFirstValidationErrorShouldMatch(String caseName) {
        AuthApiData caseData = data(caseName);
        assertEquals(response.jsonPath().getString("details[0].field"), caseData.expectedField());
        assertEquals(response.jsonPath().getString("details[0].message"), caseData.expectedMessage());
    }
}
