package com.tests.application.base;

import com.framework.config.ConfigManager;
import com.framework.driver.MobileDriverManager;
import com.framework.mobile.MobileUtils;
import com.framework.secrets.SecretManager;
import com.tests.application.pages.mobile.HomePage;
import com.tests.application.pages.mobile.LoginPage;
import org.testng.annotations.AfterMethod;

/**
 * Shared lifecycle for every Mobile spec, so an individual test class ({@code LoginTest},
 * {@code EventsTest}, {@code EventBookingE2EFlowTest}) owns nothing but its {@code @Test}
 * methods and the screen-level assertions specific to what it's covering:
 *
 * <ul>
 *     <li>{@link #ensureLoggedIn()}/{@link #ensureLoggedOut()} - the driver-acquisition +
 *     dismiss-system-dialogs + login-state plumbing every class needing a specific starting
 *     state was hand-writing identically. Each subclass's own {@code @BeforeMethod} still picks
 *     which one (or neither) fits its scenario - {@code LoginTest} needs a logged-out start
 *     since login is the thing under test there, {@code EventsTest} needs a logged-in one, and
 *     {@code MultiDeviceParallelTest} needs neither (it must set device config <i>before</i> the
 *     driver exists, so it calls {@link MobileDriverManager#getDriver()} itself).</li>
 *     <li>{@link ConfigManager#clearThreadState()} after every test, guaranteed once here
 *     instead of re-declared per class.</li>
 * </ul>
 *
 * <p>A subclass with its own test data to release (e.g. a booking made mid-test) overrides
 * {@link #tearDownTestData()}, which runs before the thread-state cleanup.</p>
 */
public abstract class BaseMobileTest {

    @AfterMethod(alwaysRun = true)
    public final void baseMobileCleanup() {
        tearDownTestData();
        ConfigManager.clearThreadState();
    }

    /** Override to release test data created during the test. No-op by default. */
    protected void tearDownTestData() {
    }

    /**
     * Acquires the driver, dismisses any system dialogs, and logs in with eventhub's shared
     * seeded account if not already logged in.
     */
    protected HomePage ensureLoggedIn() {
        MobileDriverManager.getDriver();
        MobileUtils.dismissSystemDialogsIfPresent();
        return new LoginPage().loginIfNeeded(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
    }

    /** Acquires the driver, dismisses any system dialogs, and logs out if currently logged in. */
    protected void ensureLoggedOut() {
        MobileDriverManager.getDriver();
        MobileUtils.dismissSystemDialogsIfPresent();
        new HomePage().logoutIfLoggedIn();
    }
}
