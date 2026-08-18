package com.tests.mobile.pages;

import com.framework.mobile.BaseMobilePage;
import com.framework.mobile.MobileActions;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * The Sauce Labs SwagLabs mobile app's login screen (apps/swaglabs.apk),
 * mirroring the Web layer's {@code LoginPage} (requirement.md &sect;6).
 * Locators use the app's own {@code content-desc} accessibility identifiers
 * (via {@link AppiumBy#accessibilityId}) - the same "test-*" ids Appium's
 * own inspector shows, verified live against the app rather than guessed.
 */
public class LoginPage extends BaseMobilePage {

    private static final By USERNAME_INPUT = AppiumBy.accessibilityId("test-Username");
    private static final By PASSWORD_INPUT = AppiumBy.accessibilityId("test-Password");
    private static final By LOGIN_BUTTON = AppiumBy.accessibilityId("test-LOGIN");
    // The "test-Error message" accessibility id is on a container ViewGroup with no text
    // of its own (verified live) - the actual message is in a nested TextView.
    private static final By ERROR_MESSAGE =
            By.xpath("//*[@content-desc='test-Error message']//android.widget.TextView");

    public LoginPage enterUsername(String username) {
        logger.info("Entering username");
        type(USERNAME_INPUT, username);
        return this;
    }

    public LoginPage enterPassword(String password) {
        logger.info("Entering password");
        type(PASSWORD_INPUT, password);
        return this;
    }

    /**
     * Taps LOGIN. Deliberately returns {@code void}, not the next page - same
     * rationale as the Web {@code LoginPage}: a failed login does not
     * navigate anywhere, so assuming success here would hand back a page
     * object for a screen that never loaded.
     */
    public void tapLogin() {
        logger.info("Tapping LOGIN button");
        tap(LOGIN_BUTTON);
    }

    public String getErrorMessage() {
        return getText(ERROR_MESSAGE);
    }

    public boolean isErrorDisplayed() {
        return isDisplayed(ERROR_MESSAGE);
    }

    /** Taps one of the app's own tap-to-autofill sample usernames (e.g. "standard_user"), shown directly on the login screen. */
    public LoginPage tapSampleUser(String username) {
        logger.info("Tapping sample user: {}", username);
        MobileActions.tap(AppiumBy.accessibilityId("test-" + username));
        return this;
    }
}
