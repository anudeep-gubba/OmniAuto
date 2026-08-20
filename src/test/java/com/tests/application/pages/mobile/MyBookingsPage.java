package com.tests.application.pages.mobile;

import com.framework.mobile.BaseMobilePage;
import com.framework.mobile.PlatformLocator;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * The eventhub mobile app's My Bookings screen: one card per booking (event name, booking
 * reference, status, tickets, total, booked date - all glued into one composite accessibility
 * label per card, verified live, the same pattern {@code EventCardComponent} documents for the
 * Events listing) plus a "Clear all bookings" action. Reached via
 * {@link BookingConfirmationPage#tapViewMyBookings()}.
 */
public class MyBookingsPage extends BaseMobilePage {

    private static final By PAGE_TITLE = AppiumBy.accessibilityId("My Bookings");
    private static final By CLEAR_ALL_BOOKINGS_BUTTON = AppiumBy.accessibilityId("Clear all bookings");

    public boolean isDisplayed() {
        return isDisplayed(PAGE_TITLE);
    }

    /** Whether a booking card mentioning both {@code eventName} and {@code bookingReference} is present. */
    public boolean hasBookingFor(String eventName, String bookingReference) {
        // Platform-specific element class name (see PlatformLocator): iOS's
        // XCUIElementTypeOther/"name" vs. Android's android.view.View/"content-desc".
        By card = PlatformLocator.of(
                By.xpath(String.format(
                        "//android.view.View[contains(@content-desc,'%s') and contains(@content-desc,'%s')]",
                        eventName, bookingReference)),
                By.xpath(String.format(
                        "//XCUIElementTypeOther[contains(@name,'%s') and contains(@name,'%s')]",
                        eventName, bookingReference)));
        boolean present = isDisplayed(card);
        logger.info("My Bookings shows a card for '{}' ({}): {}", eventName, bookingReference, present);
        return present;
    }

    /** No-op if there is nothing to clear - used for post-test cleanup, not asserted on. */
    public void clearAllBookingsIfPresent() {
        if (isDisplayed(CLEAR_ALL_BOOKINGS_BUTTON)) {
            tap(CLEAR_ALL_BOOKINGS_BUTTON);
            logger.info("Cleared all bookings");
        }
    }
}
