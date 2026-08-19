package com.tests.pages.mobile;

import com.framework.mobile.BaseMobilePage;
import com.framework.mobile.PlatformLocator;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;

/**
 * The eventhub mobile app's post-booking confirmation screen ("Booking confirmed!", a
 * server-generated booking reference, and links to My Bookings / back to Events - verified
 * live). Reached via {@link EventDetailPage#tapConfirmBooking()}.
 */
public class BookingConfirmationPage extends BaseMobilePage {

    private static final By CONFIRMED_TITLE = AppiumBy.accessibilityId("Booking confirmed!");
    // "Booking Reference" is a label; its value is the very next sibling static-text element -
    // verified live. Platform-specific element class name (see PlatformLocator): iOS's
    // XCUIElementTypeStaticText/"name" vs. Android's android.view.View/"content-desc".
    private static final By BOOKING_REFERENCE_VALUE = PlatformLocator.of(
            By.xpath("//android.view.View[@content-desc='Booking Reference']/following-sibling::android.view.View[1]"),
            By.xpath("//XCUIElementTypeStaticText[@name='Booking Reference']/following-sibling::XCUIElementTypeStaticText[1]"));
    private static final By VIEW_MY_BOOKINGS_BUTTON = AppiumBy.accessibilityId("View My Bookings");

    public boolean isDisplayed() {
        return isDisplayed(CONFIRMED_TITLE);
    }

    public String getBookingReference() {
        String reference = getText(BOOKING_REFERENCE_VALUE);
        logger.info("Booking reference: {}", reference);
        return reference;
    }

    public MyBookingsPage tapViewMyBookings() {
        tap(VIEW_MY_BOOKINGS_BUTTON);
        logger.info("Tapped 'View My Bookings'");
        return new MyBookingsPage();
    }
}
