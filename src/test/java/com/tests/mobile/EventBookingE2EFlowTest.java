package com.tests.mobile;

import com.framework.config.ConfigManager;
import com.framework.driver.MobileDriverManager;
import com.framework.mobile.MobileUtils;
import com.framework.secrets.SecretManager;
import com.framework.testdata.TestDataManager;
import com.tests.components.mobile.EventCardComponent;
import com.tests.pages.mobile.BookingConfirmationPage;
import com.tests.pages.mobile.EventDetailPage;
import com.tests.pages.mobile.EventsPage;
import com.tests.pages.mobile.HomePage;
import com.tests.pages.mobile.LoginPage;
import com.tests.pages.mobile.MyBookingsPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * The mobile equivalent of {@link com.tests.api.EventBookingE2EFlowTest}: the same
 * "login -&gt; browse -&gt; book -&gt; confirm -&gt; shows up in My Bookings" journey, driven
 * through the real app UI (Appium/XCUITest) instead of direct HTTP calls - each step chains a
 * real value read from the previous screen (the event's name, the booking reference generated
 * on confirmation) into the next assertion, rather than exercising any one screen in isolation.
 * Individual screen positive/negative cases live in {@link LoginTest}/{@link EventsTest}.
 */
public class EventBookingE2EFlowTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBookingE2EFlowTest.class);

    private MyBookingsPage myBookingsPage;

    @BeforeMethod(alwaysRun = true)
    public void logIn() {
        MobileDriverManager.getDriver();
        MobileUtils.dismissSystemDialogsIfPresent();

        HomePage homePage = new LoginPage().loginIfNeeded(
                SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
        assertTrue(homePage.isDisplayed(), "Login should complete before starting the booking flow.");
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        if (myBookingsPage != null) {
            myBookingsPage.clearAllBookingsIfPresent();
            myBookingsPage = null;
        }
        ConfigManager.clearThreadState();
    }

    @Test(groups = {"smoke", "mobile"})
    public void bookingFlowFromLoginThroughConfirmationAndMyBookingsWorksEndToEnd() {
        MobileBookingTestCase data = TestDataManager.load("mobile-booking.json")
                .get("standardBooking", MobileBookingTestCase.class);
        LOGGER.info("[{}] {} - {}", data.testCaseId(), data.testCaseName(), data.description());

        EventsPage eventsPage = new HomePage().browseEvents();

        List<EventCardComponent> cards = eventsPage.getEventCards();
        assertTrue(cards.size() > 0, "Events listing should show at least one bookable event.");
        EventCardComponent firstCard = cards.get(0);
        String eventName = firstCard.getName();
        firstCard.tapBookNow();

        EventDetailPage detailPage = new EventDetailPage();
        assertTrue(detailPage.isDisplayed(), "Book Now should navigate to the event detail/booking screen.");

        BookingConfirmationPage confirmationPage = detailPage
                .enterFullName(data.fullName())
                .enterPhone(data.phone())
                .tapConfirmBooking();

        assertTrue(confirmationPage.isDisplayed(), "Confirming should show the 'Booking confirmed!' screen.");
        String bookingReference = confirmationPage.getBookingReference();
        assertFalse(bookingReference.isBlank(), "A booking reference should be generated.");

        myBookingsPage = confirmationPage.tapViewMyBookings();
        assertTrue(myBookingsPage.isDisplayed(), "'View My Bookings' should navigate to the My Bookings screen.");
        assertTrue(myBookingsPage.hasBookingFor(eventName, bookingReference),
                "My Bookings should show a card for '" + eventName + "' (" + bookingReference + ").");
    }
}
