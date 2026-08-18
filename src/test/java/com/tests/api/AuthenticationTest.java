package com.tests.api;

import com.framework.api.responses.AuthResponse;
import com.framework.api.responses.MeResponse;
import com.framework.api.services.AuthenticationService;
import com.framework.exceptions.ApiAuthenticationException;
import com.framework.secrets.SecretManager;
import com.framework.utils.RandomDataUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 7 validation (requirement.md &sect;38 API checklist: authentication)
 * against eventhub's real, live API - not a mock.
 */
public class AuthenticationTest {

    private final AuthenticationService authService = new AuthenticationService();

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        authService.logout();
    }

    @Test(groups = {"smoke", "api"})
    public void registeringANewUserReturnsAUsableToken() {
        String email = RandomDataUtils.uniqueEmail("framework.test");

        AuthResponse response = authService.register(email, "Framework@2026");

        assertTrue(response.success());
        assertNotNull(response.token());
        assertEquals(response.user().email(), email);

        // The token register() stored is immediately usable for a protected call.
        MeResponse me = authService.me();
        assertEquals(me.user().email(), email);
    }

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

    @Test(groups = "api")
    public void loginWithWrongPasswordFails() {
        ApiAuthenticationException exception = expectThrows(ApiAuthenticationException.class,
                () -> authService.login(SecretManager.get("EVENTHUB_EMAIL"), "DefinitelyWrongPassword1!"));
        assertTrue(exception.getMessage().contains("Login failed"));
    }

    @Test(groups = "api")
    public void callingProtectedEndpointWithoutLoggingInFailsFast() {
        expectThrows(ApiAuthenticationException.class, authService::me);
    }
}
