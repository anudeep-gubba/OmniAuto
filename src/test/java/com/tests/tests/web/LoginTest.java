package com.tests.tests.web;

import com.framework.config.ConfigManager;
import com.tests.application.base.BaseWebTest;
import com.tests.application.testdata.LoginTestCase.LoginData;
import com.tests.application.testdata.LoginTestCase;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.pages.web.HomePage;
import com.tests.application.pages.web.LoginPage;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Phase 5 validation (requirement.md &sect;38 WEB checklist: valid login, invalid login)
 * against eventhub.rahulshettyacademy.com. The checklist's third item, cross-browser support,
 * is not covered by a test in this class - CI's own browser matrix (see CI_CD.md /
 * {@code .github/workflows/ci.yml}) already runs the entire suite, this class included, once
 * per browser as separate jobs, which is both a stronger check (every test, not just login) and
 * a cheaper one (parallel CI jobs, not a sequential in-test loop opening two driver sessions)
 * than a dedicated per-test browser loop would be. Browser is environment-level config
 * (`-Dbrowser=...`) precisely so a test never has to set it itself.
 *
 * <p>Real account credentials, resolved via {@code ${{EVENTHUB_EMAIL}}}/
 * {@code ${{EVENTHUB_PASSWORD}}} placeholders in {@code testdata/json/web/web.json} - which
 * {@link com.framework.testdata.PlaceholderResolver} resolves from {@link
 * com.framework.secrets.SecretManager}, itself backed by {@code .secret.env} (never hardcoded,
 * never committed) - unlike saucedemo's intentionally-public sample accounts used earlier in
 * this phase, these are a real registered account, so they get the full secret-management
 * treatment RULE 6 requires.</p>
 *
 * <p>Test data (metadata/data per case, for easy identification in a failure or a report)
 * lives in {@code testdata/json/web/web.json}, separate from {@code testdata/json/api/api.json}
 * (API) and {@code testdata/json/android/android.json}/{@code testdata/json/ios/ios.json} (Mobile) -
 * "maintain separate files per surface" per the task this suite was written for.</p>
 */
public class LoginTest extends BaseWebTest {

    // Also "sanity": the narrowest possible "is the app fundamentally alive" checkpoint -
    // one representative live test per surface (Web/Mobile/API), distinct from and smaller
    // than "smoke" (broad - includes every framework-internal unit test too). See README.md.
    @Test(groups = {"smoke", "sanity", "web"})
    public void validLoginNavigatesToHomePage() {
        LoginData data = TestDataSurface.WEB.getCaseData("validLogin", LoginTestCase.class);

        LoginPage loginPage = new LoginPage();
        loginPage.open(ConfigManager.getBaseUrl())
                .enterEmail(data.email())
                .enterPassword(data.password())
                .clickLogin();

        HomePage homePage = new HomePage();
        assertTrue(homePage.isDisplayed(), "Home page should show the logged-in nav after a valid login.");
    }

    @Test(groups = {"smoke", "web"})
    public void invalidLoginShowsErrorAndStaysOnLoginPage() {
        LoginData data = TestDataSurface.WEB.getCaseData("invalidLogin", LoginTestCase.class);

        LoginPage loginPage = new LoginPage();
        loginPage.open(ConfigManager.getBaseUrl())
                .enterEmail(data.email())
                .enterPassword(data.password())
                .clickLogin();

        assertTrue(loginPage.isErrorDisplayed(), "An error message should be displayed for a wrong password.");
        assertTrue(loginPage.getErrorMessage().toLowerCase().contains("invalid"));
    }

}
