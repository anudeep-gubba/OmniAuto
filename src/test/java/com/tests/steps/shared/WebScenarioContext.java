package com.tests.steps.shared;

import com.framework.config.ConfigManager;
import com.framework.secrets.SecretManager;
import com.tests.application.pages.web.EventsPage;
import com.tests.application.pages.web.HomePage;
import com.tests.application.pages.web.LoginPage;

import static com.framework.utils.Verify.assertTrue;

/**
 * One instance per scenario (Cucumber + {@code cucumber-picocontainer}), shared across a Web
 * scenario's step-definition classes and {@code com.tests.hooks.WebHooks} - the
 * composition-based replacement for the old inheritance-based {@code BaseWebTest}.
 */
public class WebScenarioContext {

    public LoginPage loginPage;
    public HomePage homePage;
    public EventsPage eventsPage;

    /** Opens the login page and signs in with eventhub's shared seeded account. */
    public HomePage loginWithSeededAccount() {
        loginPage = new LoginPage();
        loginPage.open(ConfigManager.getBaseUrl())
                .enterEmail(SecretManager.get("EVENTHUB_EMAIL"))
                .enterPassword(SecretManager.get("EVENTHUB_PASSWORD"))
                .clickLogin();

        // clickLogin() deliberately does not wait for the async login API call to complete
        // (see its Javadoc). Navigating away before it does would abandon the pending request
        // and load the next URL unauthenticated - which here just redirects straight back to
        // /login. Waiting for the logged-in nav first is what actually confirms login succeeded.
        homePage = new HomePage();
        assertTrue(homePage.isDisplayed(), "Login should complete before proceeding.");
        return homePage;
    }
}
