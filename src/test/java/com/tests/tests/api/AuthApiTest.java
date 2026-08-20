package com.tests.tests.api;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.tests.application.requests.AuthRequest;
import com.tests.application.responses.AuthResponse;
import com.tests.application.responses.MeResponse;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.AuthApiTestCase.AuthApiData;
import com.tests.application.testdata.api.AuthApiTestCase;
import com.framework.exceptions.ApiAuthenticationException;
import com.framework.utils.RandomDataUtils;
import com.tests.application.base.BaseApiTest;
import org.testng.annotations.Test;

import java.util.Map;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertFalse;
import static com.framework.utils.Verify.assertNotNull;
import static com.framework.utils.Verify.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Full positive/negative coverage of eventhub's {@code /auth/*} endpoints (register, login, me),
 * against the live API - not a mock. Every assertion below (exact HTTP status, exact error
 * message/validation field) was confirmed directly against the running API before being encoded
 * here, not assumed from the OpenAPI spec alone.
 *
 * <p>Test data (metadata/data per case, for easy identification in a failure or a report)
 * lives in {@code testdata/json/api/api.json}, separate from {@code testdata/json/web/web.json}
 * (Web) and {@code testdata/json/android/android.json}/{@code testdata/json/ios/ios.json} (Mobile) -
 * "maintain separate files per surface" per the task this suite was written for.</p>
 */
public class AuthApiTest extends BaseApiTest {

    // ---------------------------------------------------------------- register: positive

    @Test(groups = {"smoke", "api", "auth", "positive"})
    public void registeringANewUserReturnsAUsableToken() {
        AuthApiData data = TestDataSurface.API.getCaseData("registerNewUser", AuthApiTestCase.class);
        String email = RandomDataUtils.uniqueEmail("auth.register");

        AuthResponse response = authService.register(email, data.password());

        assertTrue(response.success(), "Registration response should report success.");
        assertNotNull(response.token(), "Registration response should include a usable auth token.");
        assertTrue(response.user().id() > 0, "Registered user should be assigned a positive numeric id.");
        assertEquals(response.user().email(), email, "Registered user's email should match what was submitted.");

        // The token register() stored is immediately usable for a protected call.
        MeResponse me = authService.me();
        assertEquals(me.user().email(), email, "GET /auth/me should return the same account the token was just issued for.");
    }

    // ---------------------------------------------------------------- register: negative

    @Test(groups = {"api", "auth", "negative"})
    public void registeringWithAnAlreadyRegisteredEmailFails() {
        // The seeded account (used by loginWithExistingAccountWorks etc.) is guaranteed to exist.
        AuthApiData data = TestDataSurface.API.getCaseData("registerDuplicateEmail", AuthApiTestCase.class);
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/register")
                .body(new AuthRequest(data.email(), data.password())));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), data.expectedError());
        assertFalse(response.jsonPath().getBoolean("success"), "Response should report success=false for a rejected registration.");
    }

    @Test(groups = {"api", "auth", "negative"})
    public void registeringWithAnInvalidEmailFormatFails() {
        AuthApiData data = TestDataSurface.API.getCaseData("registerInvalidEmailFormat", AuthApiTestCase.class);
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/register")
                .body(new AuthRequest(data.email(), data.password())));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("details[0].field"), data.expectedField());
        assertEquals(response.jsonPath().getString("details[0].message"), data.expectedMessage());
    }

    @Test(groups = {"api", "auth", "negative"})
    public void registeringWithAPasswordShorterThanSixCharsFails() {
        AuthApiData data = TestDataSurface.API.getCaseData("registerShortPassword", AuthApiTestCase.class);
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/register")
                .body(new AuthRequest(RandomDataUtils.uniqueEmail("auth.shortpw"), data.password())));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("details[0].field"), data.expectedField());
        assertEquals(response.jsonPath().getString("details[0].message"), data.expectedMessage());
    }

    @Test(groups = {"api", "auth", "negative"})
    public void registeringWithNoBodyFieldsAtAllReturnsValidationErrorsForBoth() {
        AuthApiData data = TestDataSurface.API.getCaseData("registerEmptyBody", AuthApiTestCase.class);
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/register").body(Map.of()));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), data.expectedError());
        java.util.List<String> fields = response.jsonPath().getList("details.field", String.class);
        assertTrue(fields.contains("email"), "Missing email should be flagged: " + fields);
        assertTrue(fields.contains("password"), "Missing password should be flagged: " + fields);
    }

    // ---------------------------------------------------------------- login: positive

    @Test(groups = {"smoke", "api", "auth", "positive"})
    public void loginWithExistingAccountWorks() {
        AuthApiData data = TestDataSurface.API.getCaseData("loginExistingAccount", AuthApiTestCase.class);
        AuthResponse response = authService.login(data.email(), data.password());

        assertTrue(response.success(), "Login response should report success.");
        assertNotNull(response.token(), "Login response should include a usable auth token.");
        assertEquals(response.user().email(), data.email(), "Logged-in user's email should match the account logged in with.");
    }

    // ---------------------------------------------------------------- login: negative

    @Test(groups = {"api", "auth", "negative"})
    public void loginWithWrongPasswordFails() {
        AuthApiData data = TestDataSurface.API.getCaseData("loginWrongPassword", AuthApiTestCase.class);
        ApiAuthenticationException exception = expectThrows(ApiAuthenticationException.class,
                () -> authService.login(data.email(), data.password()));
        assertTrue(exception.getMessage().contains("Login failed"), "Exception message should indicate login failed.");
    }

    /**
     * The OpenAPI spec documents this case as a 404 "User not found" - but the live API does
     * not actually distinguish "no such account" from "wrong password" (both return this same
     * 400 "Invalid email or password", confirmed live), which is the more defensible behavior
     * anyway: it avoids leaking which emails are registered to an unauthenticated caller. This
     * test locks in the real, observed contract rather than the spec's stale documented one.
     */
    @Test(groups = {"api", "auth", "negative"})
    public void loginWithANonexistentEmailReturns400WithAGenericMessage() {
        AuthApiData data = TestDataSurface.API.getCaseData("loginNonexistentEmail", AuthApiTestCase.class);
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/login")
                .body(new AuthRequest(RandomDataUtils.uniqueEmail("auth.nosuchuser"), data.password())));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), data.expectedError());
    }

    @Test(groups = {"api", "auth", "negative"})
    public void loginWithMissingPasswordFieldFailsValidation() {
        AuthApiData data = TestDataSurface.API.getCaseData("loginMissingPasswordField", AuthApiTestCase.class);
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/login")
                .body(Map.of("email", data.email())));

        response.assertStatusCode(data.expectedStatusCode());
    }

    // ---------------------------------------------------------------- me: positive/negative

    @Test(groups = {"api", "auth", "negative"})
    public void callingProtectedEndpointWithoutLoggingInFailsFast() {
        // No login() called this test - hasAuthToken() is false, so the service short-circuits
        // before ever making the call, rather than letting the API 401 it.
        expectThrows(ApiAuthenticationException.class, authService::me);
    }

    @Test(groups = {"api", "auth", "negative"})
    public void callingMeWithAGarbageBearerTokenReturns401() {
        AuthApiData data = TestDataSurface.API.getCaseData("meGarbageToken", AuthApiTestCase.class);
        ApiClient.setAuthToken(data.token());

        ApiAuthenticationException exception = expectThrows(ApiAuthenticationException.class, authService::me);
        assertTrue(exception.getMessage().contains("Token validation failed"), "Exception message should indicate token validation failed.");
    }

    @Test(groups = {"api", "auth", "negative"})
    public void callingMeWithNoAuthorizationHeaderAtAllReturns401() {
        AuthApiData data = TestDataSurface.API.getCaseData("meWithoutAuth", AuthApiTestCase.class);
        // Bypasses AuthenticationService's own "no token set" short-circuit, to prove the API
        // itself (not just the framework's client-side guard) rejects the call.
        ApiResponse response = ApiClient.execute(ApiRequest.get("/auth/me"));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), data.expectedError());
    }
}
