package com.tests.mobile;

import com.framework.config.ConfigManager;
import com.framework.driver.MobileDriverManager;
import com.framework.mobile.MobileUtils;
import com.framework.testdata.TestDataManager;
import com.tests.pages.mobile.HomePage;
import com.tests.pages.mobile.LoginPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
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
 * <p>Test data (id/name/description per case, for easy identification in a failure or a
 * report) lives in {@code testdata/json/mobile-login.json}, separate from
 * {@code testdata/json/login.json} (Web/API) - "maintain separate files per surface" per the
 * task this suite was written for.</p>
 */
public class LoginTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginTest.class);

    // alwaysRun = true: without this, TestNG silently skips this method whenever a group
    // include-filter (-Dgroups=...) is active - see com.tests.web.EventsTest's identical note.
    @BeforeMethod(alwaysRun = true)
    public void launchLoggedOut() {
        MobileDriverManager.getDriver();
        MobileUtils.dismissSystemDialogsIfPresent();
        // Guarantees a logged-out start regardless of what a previous test in this run left
        // behind - see HomePage.logoutIfLoggedIn()'s javadoc.
        new HomePage().logoutIfLoggedIn();
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ConfigManager.clearThreadState();
    }

    private static MobileLoginTestCase testCase(String name) {
        MobileLoginTestCase testCase = TestDataManager.load("mobile-login.json").get(name, MobileLoginTestCase.class);
        LOGGER.info("[{}] {} - {}", testCase.testCaseId(), testCase.testCaseName(), testCase.description());
        return testCase;
    }

    // Also "sanity": the narrowest "is the app fundamentally alive" checkpoint - one
    // representative live test per surface, distinct from "smoke". See README.md.
    @Test(groups = {"smoke", "sanity", "mobile"})
    public void validCredentialsLogInAndShowHomeScreen() {
        MobileLoginTestCase data = testCase("validCredentials");

        LoginPage loginPage = new LoginPage();
        loginPage.enterEmail(data.email()).enterPassword(data.password()).tapSignIn();

        HomePage homePage = new HomePage();
        assertTrue(homePage.isDisplayed(), "Home screen should show the logged-in header after a valid login.");
    }

    @Test(groups = {"smoke", "mobile"})
    public void blankCredentialsShowRequiredFieldValidation() {
        testCase("blankCredentials");

        LoginPage loginPage = new LoginPage();
        loginPage.tapSignIn();

        assertTrue(loginPage.isEmailRequiredErrorDisplayed(), "'Email is required' should be shown for a blank email.");
        assertTrue(loginPage.isPasswordRequiredErrorDisplayed(), "'Password is required' should be shown for a blank password.");
        assertTrue(loginPage.isDisplayed(), "A validation failure should not navigate away from the login screen.");
    }

    @Test(groups = "mobile")
    public void malformedEmailShowsInvalidEmailValidation() {
        MobileLoginTestCase data = testCase("malformedEmail");

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
        MobileLoginTestCase data = testCase("incorrectPassword");

        LoginPage loginPage = new LoginPage();
        loginPage.enterEmail(data.email()).enterPassword(data.password()).tapSignIn();

        HomePage homePage = new HomePage();
        assertTrue(homePage.isDisplayed(), "This build's mock auth logs in regardless of password correctness.");
    }
}
