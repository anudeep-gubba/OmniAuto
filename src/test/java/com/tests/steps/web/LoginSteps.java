package com.tests.steps.web;

import com.framework.config.ConfigManager;
import com.tests.application.pages.web.HomePage;
import com.tests.application.pages.web.LoginPage;
import com.tests.application.testdata.LoginTestCase;
import com.tests.application.testdata.LoginTestCase.LoginData;
import com.tests.application.testdata.TestDataSurface;
import com.tests.steps.shared.WebScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.framework.utils.Verify.assertTrue;

/**
 * Steps behind {@code features/web/login.feature} - a mechanical lift of the old
 * {@code com.tests.tests.web.LoginTest} {@code @Test} method bodies into Given/When/Then steps;
 * every page-object call and assertion is unchanged.
 */
public class LoginSteps {

    private final WebScenarioContext context;

    public LoginSteps(WebScenarioContext context) {
        this.context = context;
    }

    @Given("I am on the login page")
    public void iAmOnTheLoginPage() {
        context.loginPage = new LoginPage().open(ConfigManager.getBaseUrl());
    }

    @When("I log in with the {string} web test data")
    public void iLogInWithTheWebTestData(String caseName) {
        LoginData data = TestDataSurface.WEB.getCaseData(caseName, LoginTestCase.class);
        context.loginPage.enterEmail(data.email())
                .enterPassword(data.password())
                .clickLogin();
    }

    @Then("the home page should be displayed")
    public void theHomePageShouldBeDisplayed() {
        context.homePage = new HomePage();
        assertTrue(context.homePage.isDisplayed(), "Home page should show the logged-in nav after a valid login.");
    }

    @Then("an error message should be displayed")
    public void anErrorMessageShouldBeDisplayed() {
        assertTrue(context.loginPage.isErrorDisplayed(), "An error message should be displayed for a wrong password.");
    }

    @And("the error message should mention {string} credentials")
    public void theErrorMessageShouldMentionCredentials(String keyword) {
        assertTrue(context.loginPage.getErrorMessage().toLowerCase().contains(keyword),
                "Error message should mention '" + keyword + "' credentials, was: " + context.loginPage.getErrorMessage());
    }
}
