package com.tests.application.components.mobile;

import com.framework.exceptions.ElementInteractionException;
import com.framework.mobile.BaseMobileComponent;
import com.framework.mobile.MobileActions;
import com.framework.mobile.PlatformLocator;
import com.framework.secrets.SensitiveDataMasker;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * The header present on every post-login screen of the eventhub mobile app (Home/Events/My
 * Bookings): the logged-in user's email and Logout, mirroring the Web layer's
 * {@code HeaderComponent} (title/email/Logout, verified live to be siblings under one
 * unnamed container - rooted here on that container via a locator anchored to the Logout
 * button, since the container itself has no accessibility id of its own).
 *
 * <p>Tapping Logout opens a confirmation dialog ("Log out?" / Cancel / Logout - verified
 * live), whose own confirm button shares the exact same accessibility id ("Logout") as the
 * header button that opened it. {@link #logout()} disambiguates the second tap with an XPath
 * anchored to the dialog's own title text, rather than risking the accessibility-id lookup
 * matching the now-hidden header button instead. That XPath's element class name is
 * platform-specific (see PlatformLocator), same as the root locator: on Android, a real Flutter
 * button (Logout here, same as Sign In on the login screen) renders as {@code
 * android.widget.Button}, while a non-interactive label like the dialog's own "Log out?" title
 * or {@link #LOGGED_IN_EMAIL} below renders as {@code android.view.View} - verified live via
 * {@code uiautomator dump} on both the pre- and post-login screens.</p>
 */
public class HeaderComponent extends BaseMobileComponent {

    private static final By LOGOUT_BUTTON = AppiumBy.accessibilityId("Logout");
    private static final By LOGOUT_CONFIRM_BUTTON = PlatformLocator.of(
            By.xpath("//android.view.View[@content-desc='Log out?']/following::android.widget.Button[@content-desc='Logout'][1]"),
            By.xpath("//XCUIElementTypeStaticText[@name='Log out?']/following::XCUIElementTypeButton[@name='Logout'][1]"));
    private static final By LOGGED_IN_EMAIL = PlatformLocator.of(
            By.xpath(".//android.view.View[contains(@content-desc,'@')]"),
            By.xpath(".//XCUIElementTypeStaticText[contains(@name,'@')]"));

    public HeaderComponent() {
        super(PlatformLocator.of(
                By.xpath("//android.widget.Button[@content-desc='Logout']/.."),
                By.xpath("//XCUIElementTypeButton[@name='Logout']/..")));
    }

    public void logout() {
        MobileActions.tap(LOGOUT_BUTTON);
        MobileActions.tap(LOGOUT_CONFIRM_BUTTON);
        logger.info("Logged out");
    }

    public String getLoggedInUserEmail() {
        String email = textOf(LOGGED_IN_EMAIL);
        logger.info("Logged-in user email shown in header: {}", SensitiveDataMasker.mask(email));
        return email;
    }

    public boolean isLoggedIn() {
        try {
            return !find(LOGGED_IN_EMAIL).getText().isBlank();
        } catch (ElementInteractionException e) {
            return false;
        }
    }
}
