package com.tests.api;

import com.framework.api.requests.AuthRequest;
import com.framework.api.responses.AuthResponse;
import com.framework.api.services.AuthenticationService;
import com.framework.exceptions.ApiAuthenticationException;
import com.framework.secrets.SensitiveDataMasker;
import com.framework.testdata.TestDataManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 9 validation: TestNG {@code @DataProvider} + JSON/YAML test data, with automatic
 * {@code ${{...}}} secret-placeholder resolution (requirement.md &sect;9, &sect;14,
 * &sect;15, &sect;38), driven against eventhub's real, live API - not a mock. See
 * {@code com.tests.base.TestDataManagerTest} for the same files exercised without any
 * network call.
 */
public class DataDrivenLoginTest {

    private final AuthenticationService authService = new AuthenticationService();

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        authService.logout();
    }

    @Test(groups = {"smoke", "api"})
    public void jsonSourcedCredentialsLogInSuccessfully() {
        // Object-root JSON "validLogin" record, resolved straight into AuthRequest - an
        // existing request DTO reused rather than a test-only lookalike (RULE 5).
        AuthRequest credentials = TestDataManager.load("login.json").get("validLogin", AuthRequest.class);

        AuthResponse response = authService.login(credentials.email(), credentials.password());

        assertTrue(response.success());
        assertEquals(response.user().email(), credentials.email());
    }

    @DataProvider(name = "loginAttempts")
    public Object[][] loginAttempts() {
        // Array-root YAML: each row deserialized straight into LoginAttempt.
        return TestDataManager.load("login-attempts.yaml").dataProvider(LoginAttempt.class);
    }

    @Test(groups = "api", dataProvider = "loginAttempts")
    public void yamlSourcedAttemptsMatchTheirExpectedOutcome(LoginAttempt attempt) {
        if (attempt.expectSuccess()) {
            AuthResponse response = authService.login(attempt.email(), attempt.password());
            assertTrue(response.success());
        } else {
            ApiAuthenticationException exception = expectThrows(ApiAuthenticationException.class,
                    () -> authService.login(attempt.email(), attempt.password()));
            assertTrue(exception.getMessage().contains("Login failed"));
        }
    }

    /**
     * This test's own YAML row shape - unlike {@link AuthRequest}, not a reusable API DTO.
     *
     * <p><b>Found in practice, not assumed (Phase 14):</b> {@code allure-testng} automatically
     * records every {@code @DataProvider} row into the Allure result's {@code parameters}
     * array via the row object's own {@code toString()} - completely bypassing
     * {@link SensitiveDataMasker}, since that capture happens inside {@code allure-testng}'s
     * own AspectJ-woven interceptor, not through any framework logging call site. A live run
     * confirmed the record's auto-generated {@code toString()} put the raw password straight
     * into {@code allure-results/*-result.json}. Any {@code @DataProvider} row type carrying a
     * secret field needs a custom {@code toString()} like this one - there is no central place
     * to fix this once, because Allure's capture is outside the framework's own code entirely.
     */
    public record LoginAttempt(String name, String email, String password, boolean expectSuccess) {
        @Override
        public String toString() {
            return "LoginAttempt[name=" + name + ", email=" + email + ", password=" + SensitiveDataMasker.MASK
                    + ", expectSuccess=" + expectSuccess + "]";
        }
    }
}
