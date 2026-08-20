package com.tests.tests.mobile;

import com.tests.application.base.BaseMobileTest;
import com.tests.application.testdata.LoginTestCase.LoginData;
import com.tests.application.testdata.LoginTestCase;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.pages.mobile.HomePage;
import com.tests.application.pages.mobile.LoginPage;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Mobile login coverage for the eventhub app (apps/eventhub-app-simulator.app on iOS,
 * apps/eventhub-app-release.apk on Android) - replaces the earlier Sauce Labs SwagLabs demo
 * app suite (apps/swag.app / apps/swaglabs.apk, both removed) now that the app under test is
 * eventhub's own mobile client, the same product the Web/API suites already cover.
 *
 * <p>Every locator/behavior here was verified live against a real iPhone 17 Pro Simulator
 * session (Appium + XCUITest, page source captured directly), iOS first per the task this
 * suite was written for - see {@link LoginPage}'s class javadoc for why "negative login" means
 * client-side validation, not a server-rejected wrong password, in this particular build.</p>
 *
 * <p>Test data (metadata/data per case, for easy identification in a failure or a report)
 * lives in {@code testdata/json/android/android.json}/{@code testdata/json/ios/ios.json} - see
 * {@link TestDataSurface#currentMobile()} for which one a given run reads - separate from
 * {@code testdata/json/web/web.json}/{@code testdata/json/api/api.json} - "maintain separate
 * files per surface" per the task this suite was written for.</p>
 */
public class LoginTest extends BaseMobileTest {

    // alwaysRun = true: without this, TestNG silently skips this method whenever a group
    // include-filter (-Dgroups=...) is active - see com.tests.tests.web.EventsTest's identical note.
    @BeforeMethod(alwaysRun = true)
    public void launchLoggedOut() {
        // Guarantees a logged-out start regardless of what a previous test in this run left
        // behind - see HomePage.logoutIfLoggedIn()'s javadoc.
        ensureLoggedOut();
    }

    // Also "sanity": the narrowest "is the app fundamentally alive" checkpoint - one
    // representative live test per surface, distinct from "smoke". See README.md.
    @Test(groups = {"smoke", "sanity", "mobile"})
    public void validCredentialsLogInAndShowHomeScreen() {
        LoginData data = TestDataSurface.currentMobile().getCaseData("validCredentials", LoginTestCase.class);

        LoginPage loginPage = new LoginPage();
        loginPage.enterEmail(data.email()).enterPassword(data.password()).tapSignIn();

        HomePage homePage = new HomePage();
        assertTrue(homePage.isDisplayed(), "Home screen should show the logged-in header after a valid login.");
    }

    @Test(groups = {"smoke", "mobile"})
    public void blankCredentialsShowRequiredFieldValidation() {
        TestDataSurface.currentMobile().getCaseData("blankCredentials", LoginTestCase.class);

        LoginPage loginPage = new LoginPage();
        loginPage.tapSignIn();

        assertTrue(loginPage.isEmailRequiredErrorDisplayed(), "'Email is required' should be shown for a blank email.");
        assertTrue(loginPage.isPasswordRequiredErrorDisplayed(), "'Password is required' should be shown for a blank password.");
        assertTrue(loginPage.isDisplayed(), "A validation failure should not navigate away from the login screen.");
    }

    @Test(groups = "mobile")
    public void malformedEmailShowsInvalidEmailValidation() {
        LoginData data = TestDataSurface.currentMobile().getCaseData("malformedEmail", LoginTestCase.class);

        LoginPage loginPage = new LoginPage();
        loginPage.enterEmail(data.email()).enterPassword(data.password()).tapSignIn();

        assertTrue(loginPage.isInvalidEmailErrorDisplayed(), "'Enter a valid email address' should be shown for a malformed email.");
        assertTrue(loginPage.isDisplayed(), "A validation failure should not navigate away from the login screen.");
    }

    /**
     * Deliberately its own test rather than left as an unexplained surprise: this build's mock
     * auth accepts any well-formed credentials, so a genuinely wrong password is not, in fact,
     * a negative case here - see {@link LoginPage}'s class javadoc.
     */
    @Test(groups = "mobile")
    public void loginSucceedsEvenWithAnIncorrectPassword() {
        LoginData data = TestDataSurface.currentMobile().getCaseData("incorrectPassword", LoginTestCase.class);

        LoginPage loginPage = new LoginPage();
        loginPage.enterEmail(data.email()).enterPassword(data.password()).tapSignIn();

        HomePage homePage = new HomePage();
        assertTrue(homePage.isDisplayed(), "This build's mock auth logs in regardless of password correctness.");
    }
}
