package com.tests.application.pages.mobile;

import com.framework.mobile.BaseMobilePage;
import com.framework.mobile.MobileActions;
import com.framework.mobile.MobileWaits;
import com.framework.mobile.PlatformLocator;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

/**
 * The eventhub mobile app's event detail screen, which also carries the booking form itself
 * (attendee full name, phone, ticket count, Confirm Booking) - one screen, verified live,
 * unlike Web's separate detail vs. checkout split. Reached via
 * {@code EventCardComponent#tapBookNow()}.
 *
 * <p>The attendee Email field is pre-filled from the logged-in account and has no
 * accessibility id of its own once populated (verified live), so it is intentionally left
 * untouched here rather than located by a fragile positional locator. The ticket-count
 * stepper's +/- buttons are unnamed icon buttons (verified live) and aren't needed for the
 * single-ticket booking flow this Page Object drives, so they're left unimplemented rather
 * than adding a locator nothing here exercises (RULE 28 - no method that adds no real
 * value).</p>
 */
public class EventDetailPage extends BaseMobilePage {

    private static final By PAGE_TITLE = AppiumBy.accessibilityId("Event Details");
    // Placeholder-located, same platform split as LoginPage's EMAIL_INPUT/PASSWORD_INPUT: an
    // Android EditText's placeholder lives in its "hint" attribute, not content-desc - see
    // PlatformLocator.
    private static final By FULL_NAME_INPUT = PlatformLocator.of(
            By.xpath("//android.widget.EditText[@hint='Your full name']"),
            AppiumBy.accessibilityId("Your full name"));
    private static final By PHONE_INPUT = PlatformLocator.of(
            By.xpath("//android.widget.EditText[@hint='+91 98765 43210']"),
            AppiumBy.accessibilityId("+91 98765 43210"));
    private static final By CONFIRM_BOOKING_BUTTON = AppiumBy.accessibilityId("Confirm Booking");

    public boolean isDisplayed() {
        return isDisplayed(PAGE_TITLE);
    }

    /**
     * The form fields load off-screen below the fold (verified live), so each entry point
     * scrolls to itself first, then dismisses the on-screen keyboard afterward by sending a
     * Return keypress to the same element it just typed into.
     *
     * <p>Found in practice: once a field is focused, the keyboard covers roughly the bottom
     * half of the screen, which - since {@link MobileActions#scrollToElement} swipes from 80%
     * down the screen - starts the very next scroll gesture <i>inside the keyboard itself</i>
     * rather than on the underlying scroll view, so it never actually scrolls (verified live:
     * {@link #enterPhone} failed to locate the phone field after 8 such no-op scroll attempts
     * with the keyboard left open). Two dismissal strategies were tried and rejected first,
     * both verified live via screenshot to leave the keyboard fully on screen regardless:
     * {@link com.framework.mobile.MobileUtils#hideKeyboard()}, and tapping a neutral element
     * elsewhere on screen to blur the field. Sending {@link Keys#RETURN} directly to the
     * focused field is what actually dismisses it in this build - the same one Return
     * keypress also snaps the whole form (Email/Phone/Confirm Booking) into view without
     * needing a further scroll, which {@link #enterPhone}/{@link #tapConfirmBooking} rely on
     * with their own scroll calls kept only as a defensive no-op for smaller devices.</p>
     */
    public EventDetailPage enterFullName(String fullName) {
        MobileActions.scrollToElement(FULL_NAME_INPUT);
        typeAndDismissKeyboard(FULL_NAME_INPUT, fullName);
        logger.info("Entered attendee full name: {}", fullName);
        return this;
    }

    public EventDetailPage enterPhone(String phone) {
        MobileActions.scrollToElement(PHONE_INPUT);
        typeAndDismissKeyboard(PHONE_INPUT, phone);
        logger.info("Entered attendee phone: {}", phone);
        return this;
    }

    public BookingConfirmationPage tapConfirmBooking() {
        MobileActions.scrollToElement(CONFIRM_BOOKING_BUTTON);
        tap(CONFIRM_BOOKING_BUTTON);
        logger.info("Tapped Confirm Booking");
        return new BookingConfirmationPage();
    }

    /**
     * Mirrors {@link MobileActions#type(By, String)} (clear, then type) but keeps the same
     * element handle for the trailing {@link Keys#RETURN} - see {@link #enterFullName}'s
     * javadoc for why. Not routed through {@code type()}/{@link MobileActions#type} because
     * that method re-locates by {@code locator} on every call, and this screen's fields lose
     * their placeholder-derived accessibility id (this locator) the moment they hold text
     * (verified live) - re-locating after typing to send the Return key separately would fail.
     */
    private void typeAndDismissKeyboard(By locator, String value) {
        WebElement element = MobileWaits.waitForVisible(locator);
        element.clear();
        // One sendKeys call, not two: found in practice - a separate follow-up sendKeys(RETURN)
        // against the same cached element handle hit a StaleElementReferenceException (the
        // predictive-text bar appearing above the keyboard rebuilds the accessibility tree in
        // between). Passing both in a single varargs call keeps it to one command.
        element.sendKeys(value, Keys.RETURN);
    }
}
