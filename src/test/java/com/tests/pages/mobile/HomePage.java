package com.tests.pages.mobile;

import com.framework.mobile.BaseMobilePage;
import com.tests.components.mobile.HeaderComponent;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * The eventhub mobile app's post-login landing screen, mirroring the Web layer's
 * {@code HomePage}. The same header (logged-in email + Logout) is present on every
 * post-login screen (Home/Events/My Bookings) - verified live - so {@link #isDisplayed()}
 * doubles as "is any post-login screen currently showing", which is exactly what
 * {@link LoginPage#loginIfNeeded} and {@link #logoutIfLoggedIn()} need.
 */
public class HomePage extends BaseMobilePage {

    private static final By LOGOUT_BUTTON = AppiumBy.accessibilityId("Logout");
    private static final By BROWSE_EVENTS_BUTTON = AppiumBy.accessibilityId("Browse Events →");

    /**
     * Deliberately not cached as a field: re-located fresh on every call, matching the Web
     * layer's {@code HeaderComponent} pattern - see {@code com.tests.pages.web.HomePage} for
     * the concrete staleness bug that pattern was written to avoid.
     */
    public HeaderComponent header() {
        return new HeaderComponent();
    }

    public boolean isDisplayed() {
        boolean displayed = isDisplayed(LOGOUT_BUTTON);
        logger.info("Post-login header present: {}", displayed);
        return displayed;
    }

    public EventsPage browseEvents() {
        tap(BROWSE_EVENTS_BUTTON);
        logger.info("Tapped 'Browse Events'");
        return new EventsPage();
    }

    /**
     * Logs out only if actually logged in; a no-op otherwise. Used by {@code LoginTest}'s
     * {@code @BeforeMethod} to guarantee a logged-out start for every login test, regardless of
     * whatever state a previous test in the same run left the app in - see
     * {@link LoginPage#loginIfNeeded} for the mirror-image reasoning.
     */
    public void logoutIfLoggedIn() {
        if (isDisplayed()) {
            header().logout();
        }
    }
}
