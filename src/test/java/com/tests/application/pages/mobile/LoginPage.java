package com.tests.application.pages.mobile;

import com.framework.mobile.BaseMobilePage;
import com.framework.mobile.PlatformLocator;
import com.framework.secrets.SensitiveDataMasker;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * The eventhub mobile app's (apps/eventhub-app-simulator.app on iOS, apps/eventhub-app-release.apk
 * on Android) sign-in screen, mirroring the Web layer's {@code LoginPage}. Locators are the
 * Flutter app's own Semantics labels - the same value Appium exposes as {@code accessibilityId}
 * on iOS ({@code content-desc} on Android, since one Flutter Semantics tree drives both) -
 * verified live against a real iPhone 17 Pro Simulator session (XCUITest page source captured
 * via Appium, not guessed).
 *
 * <p><b>This build's login always succeeds, regardless of the password typed.</b> Verified
 * live: a deliberately wrong password for a real account still lands on the Home screen,
 * authenticated as a fixed demo account ({@code framework.qa.test@example.com}) - this build
 * mocks auth rather than validating credentials against a real backend, and per the user, the
 * one real secret-backed account this repo's {@code .secret.env} points at also stops being
 * valid every few days regardless. So "negative login" here means the client-side form
 * validation Flutter itself enforces before ever calling the network (blank fields, a
 * malformed email) - the one thing that actually rejects input in this build - not a
 * server-rejected wrong password. See {@code com.tests.steps.mobile.LoginSteps}.</p>
 *
 * <p>{@link #EMAIL_INPUT}/{@link #PASSWORD_INPUT} locate by the field's own placeholder text
 * ("you@email.com" / the obscured "••••••"), which Flutter only exposes as the field's
 * accessibility id while it is empty - fine for the one-shot "type into a freshly-loaded
 * screen" flow every test here follows, but not for re-locating a field after it already holds
 * text. That placeholder lives in a different attribute per platform - verified live via a
 * real {@code uiautomator dump}: iOS's XCUITest publishes it as the field's {@code name}
 * (so {@code AppiumBy.accessibilityId} - which matches iOS {@code name}/Android
 * {@code content-desc} - finds it directly), but on Android the same field is an
 * {@code android.widget.EditText} whose {@code content-desc} is empty; the placeholder is its
 * {@code hint} attribute instead, so Android needs its own XPath - see {@link PlatformLocator}.</p>
 */
public class LoginPage extends BaseMobilePage {

    private static final By EMAIL_INPUT = PlatformLocator.of(
            By.xpath("//android.widget.EditText[@hint='you@email.com']"),
            AppiumBy.accessibilityId("you@email.com"));
    private static final By PASSWORD_INPUT = PlatformLocator.of(
            By.xpath("//android.widget.EditText[@hint='••••••']"),
            AppiumBy.accessibilityId("••••••"));
    private static final By SIGN_IN_BUTTON = AppiumBy.accessibilityId("Sign In");
    private static final By EMAIL_REQUIRED_ERROR = AppiumBy.accessibilityId("Email is required");
    private static final By PASSWORD_REQUIRED_ERROR = AppiumBy.accessibilityId("Password is required");
    private static final By INVALID_EMAIL_ERROR = AppiumBy.accessibilityId("Enter a valid email address");

    /** True only while the login screen itself (its Sign In button) is showing. */
    public boolean isDisplayed() {
        return isDisplayed(SIGN_IN_BUTTON);
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
     * Taps Sign In. Deliberately returns {@code void}, not the next page - same rationale as
     * the Web {@code LoginPage}: a client-validation failure does not navigate anywhere, so
     * assuming success here would hand back a page object for a screen that never loaded.
     */
    public void tapSignIn() {
        tap(SIGN_IN_BUTTON);
        logger.info("Tapped Sign In button");
    }

    public boolean isEmailRequiredErrorDisplayed() {
        return isDisplayed(EMAIL_REQUIRED_ERROR);
    }

    public boolean isPasswordRequiredErrorDisplayed() {
        return isDisplayed(PASSWORD_REQUIRED_ERROR);
    }

    public boolean isInvalidEmailErrorDisplayed() {
        return isDisplayed(INVALID_EMAIL_ERROR);
    }

    /**
     * Logs in only if the login screen is actually showing; otherwise assumes a prior test in
     * this run already left the app signed in and returns the Home page as-is. Needed because
     * this framework's mobile driver capabilities don't request {@code fullReset} (verified
     * live: on iOS, a fresh Appium session reuses whatever data the already-installed app has -
     * unlike Web, where each test gets a brand-new, cookie-less browser profile), so app-level
     * login state can persist across tests in the same run. Used by every mobile test here
     * except {@code LoginTest} itself, which needs the opposite guarantee - see
     * {@link HomePage#logoutIfLoggedIn()}.
     */
    public HomePage loginIfNeeded(String email, String password) {
        if (!isDisplayed()) {
            logger.info("Already past the login screen - skipping login.");
            return new HomePage();
        }
        enterEmail(email).enterPassword(password).tapSignIn();
        return new HomePage();
    }
}
