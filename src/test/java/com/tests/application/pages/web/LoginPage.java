package com.tests.application.pages.web;

import com.framework.secrets.SensitiveDataMasker;
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
        String url = baseUrl + "/login";
        navigateTo(url);
        logger.info("Navigated to {}", url);
        return this;
    }

    public LoginPage enterEmail(String email) {
        type(EMAIL_INPUT, email);
        logger.info("Entered email: {}", SensitiveDataMasker.mask(email));
        return this;
    }

    public LoginPage enterPassword(String password) {
        type(PASSWORD_INPUT, password);
        logger.info("Entered password: {}", SensitiveDataMasker.mask(password));
        return this;
    }

    /**
     * Clicks Sign In. Deliberately returns {@code void}, not the next page: a
     * failed login (wrong credentials) does not navigate anywhere, so
     * assuming success here would hand back a page object for a page that
     * never loaded. See {@link com.tests.tests.web.LoginTest} for how the two
     * outcomes are asserted independently.
     */
    public void clickLogin() {
        click(LOGIN_BUTTON);
        logger.info("Clicked Sign In button");
    }

    public String getErrorMessage() {
        String message = getText(ERROR_TOAST_MESSAGE);
        logger.info("Error message displayed: '{}'", message);
        return message;
    }

    public boolean isErrorDisplayed() {
        return isDisplayed(ERROR_TOAST_MESSAGE);
    }
}
