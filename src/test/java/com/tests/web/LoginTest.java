package com.tests.web;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.secrets.SecretManager;
import com.tests.pages.web.HomePage;
import com.tests.pages.web.LoginPage;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Phase 5 validation (requirement.md &sect;38 WEB checklist: valid login,
 * invalid login, multiple browsers) against eventhub.rahulshettyacademy.com.
 *
 * <p>Real account credentials, resolved via {@link SecretManager} from
 * {@code .secret.env} (never hardcoded, never committed) - unlike
 * saucedemo's intentionally-public sample accounts used earlier in this
 * phase, these are a real registered account, so they get the full
 * secret-management treatment RULE 6 requires.</p>
 */
public class LoginTest {

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ConfigManager.clearThreadState();
    }

    // Also "sanity": the narrowest possible "is the app fundamentally alive" checkpoint -
    // one representative live test per surface (Web/Mobile/API), distinct from and smaller
    // than "smoke" (broad - includes every framework-internal unit test too). See README.md.
    @Test(groups = {"smoke", "sanity", "web"})
    public void validLoginNavigatesToHomePage() {
        LoginPage loginPage = new LoginPage();
        loginPage.open(ConfigManager.getBaseUrl())
                .enterEmail(SecretManager.get("EVENTHUB_EMAIL"))
                .enterPassword(SecretManager.get("EVENTHUB_PASSWORD"))
                .clickLogin();

        HomePage homePage = new HomePage();
        assertTrue(homePage.isDisplayed(), "Home page should show the logged-in nav after a valid login.");
    }

    @Test(groups = {"smoke", "web"})
    public void invalidLoginShowsErrorAndStaysOnLoginPage() {
        LoginPage loginPage = new LoginPage();
        loginPage.open(ConfigManager.getBaseUrl())
                .enterEmail(SecretManager.get("EVENTHUB_EMAIL"))
                .enterPassword("DefinitelyTheWrongPassword1!")
                .clickLogin();

        assertTrue(loginPage.isErrorDisplayed(), "An error message should be displayed for a wrong password.");
        assertTrue(loginPage.getErrorMessage().toLowerCase().contains("invalid"));
    }

    @Test(groups = "web", dataProvider = "browsers")
    public void loginWorksAcrossMultipleBrowsers(String browser) {
        ConfigManager.setOverride(ConfigKeys.BROWSER, browser);
        ConfigManager.setOverride(ConfigKeys.HEADLESS, "true");

        LoginPage loginPage = new LoginPage();
        loginPage.open(ConfigManager.getBaseUrl())
                .enterEmail(SecretManager.get("EVENTHUB_EMAIL"))
                .enterPassword(SecretManager.get("EVENTHUB_PASSWORD"))
                .clickLogin();

        HomePage homePage = new HomePage();
        assertTrue(homePage.isDisplayed(), browser + ": home page should show the logged-in nav after login.");
    }

    @DataProvider(name = "browsers")
    public Object[][] browsers() {
        return new Object[][]{{"chrome"}, {"firefox"}};
    }
}
