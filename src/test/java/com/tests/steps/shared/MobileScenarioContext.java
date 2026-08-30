package com.tests.steps.shared;

import com.framework.driver.MobileDriverManager;
import com.framework.mobile.MobileUtils;
import com.framework.secrets.SecretManager;
import com.tests.application.pages.mobile.EventsPage;
import com.tests.application.pages.mobile.HomePage;
import com.tests.application.pages.mobile.LoginPage;
import com.tests.application.pages.mobile.MyBookingsPage;

/**
 * One instance per scenario (Cucumber + {@code cucumber-picocontainer}), shared across a Mobile
 * scenario's step-definition classes and {@code com.tests.hooks.MobileHooks} - the
 * composition-based replacement for the old inheritance-based {@code BaseMobileTest}.
 *
 * <p>{@link #myBookingsPage} mirrors what {@code EventBookingE2EFlowTest} used to track via a
 * {@code tearDownTestData()} override, so {@code MobileHooks} can clear any bookings a booking-
 * flow scenario left behind.</p>
 */
public class MobileScenarioContext {

    public LoginPage loginPage;
    public HomePage homePage;
    public EventsPage eventsPage;
    public MyBookingsPage myBookingsPage;

    /** Acquires the driver, dismisses any system dialogs, and logs in with eventhub's shared seeded account if not already logged in. */
    public HomePage ensureLoggedIn() {
        MobileDriverManager.getDriver();
        MobileUtils.dismissSystemDialogsIfPresent();
        homePage = new LoginPage().loginIfNeeded(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
        return homePage;
    }

    /** Acquires the driver, dismisses any system dialogs, and logs out if currently logged in. */
    public void ensureLoggedOut() {
        MobileDriverManager.getDriver();
        MobileUtils.dismissSystemDialogsIfPresent();
        new HomePage().logoutIfLoggedIn();
    }
}
