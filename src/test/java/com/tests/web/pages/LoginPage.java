package com.tests.web.pages;

import com.framework.web.BasePage;
import org.openqa.selenium.By;

/**
 * eventhub.rahulshettyacademy.com's login page (requirement.md &sect;6
 * example: {@code LoginPage}).
 */
public class LoginPage extends BasePage {

    private static final By EMAIL_INPUT = By.id("email");
    private static final By PASSWORD_INPUT = By.id("password");
    private static final By LOGIN_BUTTON = By.id("login-btn");
    private static final By ERROR_TOAST_MESSAGE = By.cssSelector("[aria-live='polite'] p");

    public LoginPage open(String baseUrl) {
        logger.info("Navigating to Login page");
        navigateTo(baseUrl + "/login");
        return this;
    }

    public LoginPage enterEmail(String email) {
        logger.info("Entering email");
        type(EMAIL_INPUT, email);
        return this;
    }

    public LoginPage enterPassword(String password) {
        logger.info("Entering password");
        type(PASSWORD_INPUT, password);
        return this;
    }

    /**
     * Clicks Sign In. Deliberately returns {@code void}, not the next page: a
     * failed login (wrong credentials) does not navigate anywhere, so
     * assuming success here would hand back a page object for a page that
     * never loaded. See {@link com.tests.web.LoginTest} for how the two
     * outcomes are asserted independently.
     */
    public void clickLogin() {
        logger.info("Clicking Sign In button");
        click(LOGIN_BUTTON);
    }

    public String getErrorMessage() {
        return getText(ERROR_TOAST_MESSAGE);
    }

    public boolean isErrorDisplayed() {
        return isDisplayed(ERROR_TOAST_MESSAGE);
    }
}
