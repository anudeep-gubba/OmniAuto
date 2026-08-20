package com.tests.application.base;

import com.framework.config.ConfigManager;
import com.framework.secrets.SecretManager;
import com.tests.application.pages.web.HomePage;
import com.tests.application.pages.web.LoginPage;
import org.testng.annotations.AfterMethod;

import static org.testng.Assert.assertTrue;

/**
 * Shared lifecycle for every Web spec, so an individual test class ({@code LoginTest},
 * {@code EventsTest}) owns nothing but its {@code @Test} methods and the page-level assertions
 * specific to what it's covering:
 *
 * <ul>
 *     <li>{@link #loginWithSeededAccount()} - the open-login-page/sign-in-as-the-shared-account
 *     plumbing a class needing an authenticated starting state was hand-writing. {@code
 *     LoginTest} deliberately does not use it: login is the thing under test there, so it
 *     drives {@code LoginPage} itself with each test case's own data.</li>
 *     <li>{@link ConfigManager#clearThreadState()} after every test, guaranteed once here
 *     instead of re-declared per class.</li>
 * </ul>
 */
public abstract class BaseWebTest {

    @AfterMethod(alwaysRun = true)
    public final void baseWebCleanup() {
        ConfigManager.clearThreadState();
    }

    /** Opens the login page and signs in with eventhub's shared seeded account. */
    protected HomePage loginWithSeededAccount() {
        LoginPage loginPage = new LoginPage();
        loginPage.open(ConfigManager.getBaseUrl())
                .enterEmail(SecretManager.get("EVENTHUB_EMAIL"))
                .enterPassword(SecretManager.get("EVENTHUB_PASSWORD"))
                .clickLogin();

        // clickLogin() deliberately does not wait for the async login API call to complete
        // (see its Javadoc). Navigating away before it does would abandon the pending request
        // and load the next URL unauthenticated - which here just redirects straight back to
        // /login. Waiting for the logged-in nav first is what actually confirms login succeeded.
        HomePage homePage = new HomePage();
        assertTrue(homePage.isDisplayed(), "Login should complete before proceeding.");
        return homePage;
    }
}
