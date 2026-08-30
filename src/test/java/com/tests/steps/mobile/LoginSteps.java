package com.tests.steps.mobile;

import com.tests.application.pages.mobile.HomePage;
import com.tests.application.pages.mobile.LoginPage;
import com.tests.application.testdata.LoginTestCase;
import com.tests.application.testdata.LoginTestCase.LoginData;
import com.tests.application.testdata.TestDataSurface;
import com.tests.steps.shared.MobileScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.framework.utils.Verify.assertTrue;

/**
 * Steps behind {@code features/mobile/login.feature} - a mechanical lift of the old
 * {@code com.tests.tests.mobile.LoginTest} {@code @Test} method bodies into Given/When/Then
 * steps; every page-object call and assertion is unchanged.
 */
public class LoginSteps {

    private final MobileScenarioContext context;

    public LoginSteps(MobileScenarioContext context) {
        this.context = context;
    }

    @Given("the app is launched logged out")
    public void theAppIsLaunchedLoggedOut() {
        context.ensureLoggedOut();
        context.loginPage = new LoginPage();
    }

    @When("I log in with the {string} mobile test data")
    public void iLogInWithTheMobileTestData(String caseName) {
        LoginData data = TestDataSurface.currentMobile().getCaseData(caseName, LoginTestCase.class);
        context.loginPage.enterEmail(data.email()).enterPassword(data.password()).tapSignIn();
    }

    @Then("the home screen should be displayed")
    public void theHomeScreenShouldBeDisplayed() {
        context.homePage = new HomePage();
        assertTrue(context.homePage.isDisplayed(), "Home screen should show the logged-in header after a valid login.");
    }

    @When("I tap sign in with no credentials entered")
    public void iTapSignInWithNoCredentialsEntered() {
        context.loginPage.tapSignIn();
    }

    @Then("the {string} and {string} errors should be displayed")
    public void theErrorsShouldBeDisplayed(String firstError, String secondError) {
        assertErrorDisplayed(firstError);
        assertErrorDisplayed(secondError);
    }

    @And("the login screen should still be displayed")
    public void theLoginScreenShouldStillBeDisplayed() {
        assertTrue(context.loginPage.isDisplayed(), "A validation failure should not navigate away from the login screen.");
    }

    @Then("the {string} error should be displayed")
    public void theErrorShouldBeDisplayed(String error) {
        assertErrorDisplayed(error);
    }

    /**
     * Dispatches on the error name the scenario itself names, rather than assuming which check
     * a step invocation means - audit finding, verified live: the two callers above used to
     * ignore their own {string} captures and always run the same hardcoded check, so a future
     * scenario reusing this step text with different wording (e.g. asserting only "password is
     * required" on its own) would have silently checked the wrong field instead of failing for
     * the right reason.
     */
    private void assertErrorDisplayed(String errorName) {
        String normalized = errorName.toLowerCase();
        if (normalized.contains("email is required")) {
            assertTrue(context.loginPage.isEmailRequiredErrorDisplayed(), "'Email is required' should be shown for a blank email.");
        } else if (normalized.contains("password is required")) {
            assertTrue(context.loginPage.isPasswordRequiredErrorDisplayed(), "'Password is required' should be shown for a blank password.");
        } else if (normalized.contains("invalid email")) {
            assertTrue(context.loginPage.isInvalidEmailErrorDisplayed(), "'Enter a valid email address' should be shown for a malformed email.");
        } else {
            throw new IllegalArgumentException("Unknown login error name '" + errorName + "' - add a case to LoginSteps.assertErrorDisplayed for it.");
        }
    }
}
