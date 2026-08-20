package com.tests.tests.mobile;

import com.tests.application.base.BaseMobileTest;
import com.tests.application.testdata.mobile.MobileBookingTestCase.MobileBookingData;
import com.tests.application.testdata.mobile.MobileBookingTestCase;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.components.mobile.EventCardComponent;
import com.tests.application.pages.mobile.BookingConfirmationPage;
import com.tests.application.pages.mobile.EventDetailPage;
import com.tests.application.pages.mobile.EventsPage;
import com.tests.application.pages.mobile.HomePage;
import com.tests.application.pages.mobile.MyBookingsPage;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static com.framework.utils.Verify.assertFalse;
import static com.framework.utils.Verify.assertTrue;

/**
 * The mobile equivalent of {@link com.tests.tests.api.EventBookingE2EFlowTest}: the same
 * "login -&gt; browse -&gt; book -&gt; confirm -&gt; shows up in My Bookings" journey, driven
 * through the real app UI (Appium/XCUITest) instead of direct HTTP calls - each step chains a
 * real value read from the previous screen (the event's name, the booking reference generated
 * on confirmation) into the next assertion, rather than exercising any one screen in isolation.
 * Individual screen positive/negative cases live in {@link LoginTest}/{@link EventsTest}.
 */
public class EventBookingE2EFlowTest extends BaseMobileTest {

    private MyBookingsPage myBookingsPage;

    @BeforeMethod(alwaysRun = true)
    public void logIn() {
        HomePage homePage = ensureLoggedIn();
        assertTrue(homePage.isDisplayed(), "Login should complete before starting the booking flow.");
    }

    @Override
    protected void tearDownTestData() {
        if (myBookingsPage != null) {
            myBookingsPage.clearAllBookingsIfPresent();
            myBookingsPage = null;
        }
    }

    @Test(groups = {"smoke", "mobile", "e2e", "events", "bookings"})
    public void bookingFlowFromLoginThroughConfirmationAndMyBookingsWorksEndToEnd() {
        MobileBookingData data = TestDataSurface.currentMobile().getCaseData("standardBooking", MobileBookingTestCase.class);

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
