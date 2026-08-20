package com.tests.application.base;

import com.framework.secrets.SecretManager;
import com.tests.application.services.AuthenticationService;
import org.testng.annotations.AfterMethod;

/**
 * Shared lifecycle for every API spec, so an individual test class ({@code AuthApiTest},
 * {@code EventApiTest}, {@code BookingApiTest}, {@code EventBookingE2EFlowTest}) owns nothing
 * but its {@code @Test} methods and the request/assertion logic specific to what it's covering:
 *
 * <ul>
 *     <li>An {@link AuthenticationService}, shared rather than re-instantiated per class.</li>
 *     <li>{@link #loginWithSeededAccount()} - the "log in as eventhub's shared seeded account"
 *     one-liner every class that needs an authenticated session before each test was
 *     hand-writing via {@code SecretManager.get("EVENTHUB_EMAIL")}/{@code get("EVENTHUB_PASSWORD")}.</li>
 *     <li>A guaranteed {@link AuthenticationService#logout()} after every test, regardless of
 *     whether the test logged in via {@link #loginWithSeededAccount()}, registered its own
 *     throwaway account, or never authenticated at all - {@code logout()} is a safe no-op when
 *     there is nothing to clear (see its javadoc), so this never needs a null/state check.</li>
 * </ul>
 *
 * <p>A subclass that creates its own test data (a booking, an event) and needs to release it
 * <i>while still authenticated</i>, before that logout happens, overrides
 * {@link #tearDownTestData()} rather than declaring its own {@code @AfterMethod} - the ordering
 * (test data first, then logout) is guaranteed by this base class rather than left for every
 * subclass to get right independently. A subclass that doesn't need a pre-test login at all
 * (e.g. {@code AuthApiTest}, which logs in/registers as part of what it's testing) simply never
 * calls {@link #loginWithSeededAccount()} and gets none of it.</p>
 */
public abstract class BaseApiTest {

    protected final AuthenticationService authService = new AuthenticationService();

    @AfterMethod(alwaysRun = true)
    public final void baseApiCleanup() {
        tearDownTestData();
        authService.logout();
    }

    /**
     * Override to release test data (a created booking/event, etc.) while still authenticated -
     * runs before {@link AuthenticationService#logout()}. No-op by default.
     */
    protected void tearDownTestData() {
    }

    /** Logs in as eventhub's shared seeded account (the {@code EVENTHUB_EMAIL}/{@code EVENTHUB_PASSWORD} secrets). */
    protected void loginWithSeededAccount() {
        authService.login(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
    }
}
