package com.tests.steps.mobile;

import com.tests.application.components.mobile.EventCardComponent;
import com.tests.application.pages.mobile.BookingConfirmationPage;
import com.tests.application.pages.mobile.EventDetailPage;
import com.tests.application.pages.mobile.EventsPage;
import com.tests.application.pages.mobile.HomePage;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.mobile.MobileBookingTestCase;
import com.tests.application.testdata.mobile.MobileBookingTestCase.MobileBookingData;
import com.tests.steps.shared.MobileScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static com.framework.utils.Verify.assertFalse;
import static com.framework.utils.Verify.assertTrue;

/**
 * Steps behind {@code features/mobile/booking_e2e_flow.feature} - a mechanical lift of the old
 * {@code com.tests.tests.mobile.EventBookingE2EFlowTest} {@code @Test} method body into
 * Given/When/Then steps. Step phrases are deliberately distinct from {@link EventsSteps} (this
 * class shares its glue package) except {@code "the event detail page should be displayed"},
 * which is reused as-is - it is fully stateless (constructs a fresh {@link EventDetailPage} each
 * time), so sharing that one step's text is safe.
 */
public class BookingE2EFlowSteps {

    private final MobileScenarioContext context;

    private EventCardComponent notedCard;
    private String eventName;
    private BookingConfirmationPage confirmationPage;
    private String bookingReference;

    public BookingE2EFlowSteps(MobileScenarioContext context) {
        this.context = context;
    }

    @Given("I am logged in on the mobile app")
    public void iAmLoggedInOnTheMobileApp() {
        HomePage homePage = context.ensureLoggedIn();
        assertTrue(homePage.isDisplayed(), "Login should complete before starting the booking flow.");
    }

    @And("I browse events and note the first event's name")
    public void iBrowseEventsAndNoteTheFirstEventsName() {
        EventsPage eventsPage = new HomePage().browseEvents();
        List<EventCardComponent> cards = eventsPage.getEventCards();
        assertTrue(cards.size() > 0, "Events listing should show at least one bookable event.");
        notedCard = cards.get(0);
        eventName = notedCard.getName();
    }

    @When("I tap Book Now on the noted event card")
    public void iTapBookNowOnTheNotedEventCard() {
        notedCard.tapBookNow();
    }

    @When("I fill in the {string} mobile booking test data and confirm the booking")
    public void iFillInTheMobileBookingTestDataAndConfirmTheBooking(String caseName) {
        MobileBookingData data = TestDataSurface.currentMobile().getCaseData(caseName, MobileBookingTestCase.class);
        EventDetailPage detailPage = new EventDetailPage();
        confirmationPage = detailPage.enterFullName(data.fullName()).enterPhone(data.phone()).tapConfirmBooking();
    }

    @Then("the booking confirmation screen should be displayed with a generated reference")
    public void theBookingConfirmationScreenShouldBeDisplayedWithAGeneratedReference() {
        assertTrue(confirmationPage.isDisplayed(), "Confirming should show the 'Booking confirmed!' screen.");
        bookingReference = confirmationPage.getBookingReference();
        assertFalse(bookingReference.isBlank(), "A booking reference should be generated.");
    }

    @When("I tap View My Bookings")
    public void iTapViewMyBookings() {
        context.myBookingsPage = confirmationPage.tapViewMyBookings();
        assertTrue(context.myBookingsPage.isDisplayed(), "'View My Bookings' should navigate to the My Bookings screen.");
    }

    @Then("My Bookings should show a card for the booked event")
    public void myBookingsShouldShowACardForTheBookedEvent() {
        assertTrue(context.myBookingsPage.hasBookingFor(eventName, bookingReference),
                "My Bookings should show a card for '" + eventName + "' (" + bookingReference + ").");
    }
}
