package com.tests.api;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.tests.api.requests.AuthRequest;
import com.tests.api.responses.AuthResponse;
import com.tests.api.responses.MeResponse;
import com.tests.api.services.AuthenticationService;
import com.framework.exceptions.ApiAuthenticationException;
import com.framework.secrets.SecretManager;
import com.framework.utils.RandomDataUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Full positive/negative coverage of eventhub's {@code /auth/*} endpoints (register, login, me),
 * against the live API - not a mock. Every assertion below (exact HTTP status, exact error
 * message/validation field) was confirmed directly against the running API before being encoded
 * here, not assumed from the OpenAPI spec alone.
 */
public class AuthApiTest {

    private final AuthenticationService authService = new AuthenticationService();

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        authService.logout();
    }

    // ---------------------------------------------------------------- register: positive

    @Test(groups = {"smoke", "api"})
    public void registeringANewUserReturnsAUsableToken() {
        String email = RandomDataUtils.uniqueEmail("auth.register");

        AuthResponse response = authService.register(email, "Framework@2026");

        assertTrue(response.success());
        assertNotNull(response.token());
        assertTrue(response.user().id() > 0);
        assertEquals(response.user().email(), email);

        // The token register() stored is immediately usable for a protected call.
        MeResponse me = authService.me();
        assertEquals(me.user().email(), email);
    }

    // ---------------------------------------------------------------- register: negative

    @Test(groups = "api")
    public void registeringWithAnAlreadyRegisteredEmailFails() {
        // The seeded account (used by loginWithExistingAccountWorks etc.) is guaranteed to exist.
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/register")
                .body(new AuthRequest(SecretManager.get("EVENTHUB_EMAIL"), "SomeOtherPassword1!")));

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("error"), "Email already registered");
        assertFalse(response.jsonPath().getBoolean("success"));
    }

    @Test(groups = "api")
    public void registeringWithAnInvalidEmailFormatFails() {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/register")
                .body(new AuthRequest("not-an-email", "ValidPass123")));

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("details[0].field"), "email");
        assertEquals(response.jsonPath().getString("details[0].message"), "A valid email is required");
    }

    @Test(groups = "api")
    public void registeringWithAPasswordShorterThanSixCharsFails() {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/register")
                .body(new AuthRequest(RandomDataUtils.uniqueEmail("auth.shortpw"), "123")));

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("details[0].field"), "password");
        assertEquals(response.jsonPath().getString("details[0].message"), "Password must be at least 6 characters");
    }

    @Test(groups = "api")
    public void registeringWithNoBodyFieldsAtAllReturnsValidationErrorsForBoth() {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/register").body(Map.of()));

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("error"), "Validation failed");
        java.util.List<String> fields = response.jsonPath().getList("details.field", String.class);
        assertTrue(fields.contains("email"), "Missing email should be flagged: " + fields);
        assertTrue(fields.contains("password"), "Missing password should be flagged: " + fields);
    }

    // ---------------------------------------------------------------- login: positive

    // Also "sanity": see com.tests.web.LoginTest's identical note - one representative live
    // test per surface, the narrowest "is the app fundamentally alive" checkpoint.
    @Test(groups = {"smoke", "sanity", "api"})
    public void loginWithExistingAccountWorks() {
        AuthResponse response = authService.login(
                SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));

        assertTrue(response.success());
        assertNotNull(response.token());
        assertEquals(response.user().email(), SecretManager.get("EVENTHUB_EMAIL"));
    }

    // ---------------------------------------------------------------- login: negative

    @Test(groups = "api")
    public void loginWithWrongPasswordFails() {
        ApiAuthenticationException exception = expectThrows(ApiAuthenticationException.class,
                () -> authService.login(SecretManager.get("EVENTHUB_EMAIL"), "DefinitelyWrongPassword1!"));
        assertTrue(exception.getMessage().contains("Login failed"));
    }

    /**
     * The OpenAPI spec documents this case as a 404 "User not found" - but the live API does
     * not actually distinguish "no such account" from "wrong password" (both return this same
     * 400 "Invalid email or password", confirmed live), which is the more defensible behavior
     * anyway: it avoids leaking which emails are registered to an unauthenticated caller. This
     * test locks in the real, observed contract rather than the spec's stale documented one.
     */
    @Test(groups = "api")
    public void loginWithANonexistentEmailReturns400WithAGenericMessage() {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/login")
                .body(new AuthRequest(RandomDataUtils.uniqueEmail("auth.nosuchuser"), "WhateverPass1")));

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("error"), "Invalid email or password");
    }

    @Test(groups = "api")
    public void loginWithMissingPasswordFieldFailsValidation() {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/auth/login")
                .body(Map.of("email", SecretManager.get("EVENTHUB_EMAIL"))));

        response.assertStatusCode(400);
    }

    // ---------------------------------------------------------------- me: positive/negative

    @Test(groups = "api")
    public void callingProtectedEndpointWithoutLoggingInFailsFast() {
        // No login() called this test - hasAuthToken() is false, so the service short-circuits
        // before ever making the call, rather than letting the API 401 it.
        expectThrows(ApiAuthenticationException.class, authService::me);
    }

    @Test(groups = "api")
    public void callingMeWithAGarbageBearerTokenReturns401() {
        ApiClient.setAuthToken("this.is.not-a-real-jwt");

        ApiAuthenticationException exception = expectThrows(ApiAuthenticationException.class, authService::me);
        assertTrue(exception.getMessage().contains("Token validation failed"));
    }

    @Test(groups = "api")
    public void callingMeWithNoAuthorizationHeaderAtAllReturns401() {
        // Bypasses AuthenticationService's own "no token set" short-circuit, to prove the API
        // itself (not just the framework's client-side guard) rejects the call.
        ApiResponse response = ApiClient.execute(ApiRequest.get("/auth/me"));

        response.assertStatusCode(401);
        assertEquals(response.jsonPath().getString("error"), "Unauthorized");
    }
}
