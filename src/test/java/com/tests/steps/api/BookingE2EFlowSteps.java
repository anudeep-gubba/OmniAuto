package com.tests.steps.api;

import com.framework.api.ApiContext;
import com.framework.api.ApiResponse;
import com.framework.utils.DateUtils;
import com.framework.utils.RandomDataUtils;
import com.tests.application.requests.CreateBookingRequest;
import com.tests.application.requests.CreateEventRequest;
import com.tests.application.responses.BookingResponse;
import com.tests.application.responses.EventResponse;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.AuthApiTestCase;
import com.tests.application.testdata.api.AuthApiTestCase.AuthApiData;
import com.tests.application.testdata.api.BookingApiTestCase;
import com.tests.application.testdata.api.BookingApiTestCase.BookingApiData;
import com.tests.application.testdata.api.EventPayloadTestCase;
import com.tests.application.testdata.api.EventPayloadTestCase.EventPayloadData;
import com.tests.steps.shared.ApiScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertTrue;

/**
 * Steps behind {@code features/api/booking_e2e_flow.feature} - a mechanical lift of the old
 * {@code com.tests.tests.api.EventBookingE2EFlowTest} {@code @Test} method bodies into
 * Given/When/Then steps; every service call and assertion is unchanged. Step phrases are
 * deliberately "e2e"-prefixed/distinct from {@link EventSteps}/{@link BookingSteps} - this class
 * shares their glue package, and its own local fields (not {@link ApiScenarioContext}'s) back
 * these steps, so reusing another class's exact step text here would silently read the wrong
 * (uninitialized) state.
 */
public class BookingE2EFlowSteps {

    private final ApiScenarioContext context;

    private ApiResponse response;
    private CreateEventRequest lastEventRequest;
    private int lastEventId;
    private int lastBookingId;
    private int firstBookingId;
    private int firstBookingQuantity;
    private int afterFirstBooking;
    private int afterSecondBooking;

    public BookingE2EFlowSteps(ApiScenarioContext context) {
        this.context = context;
    }

    private static EventPayloadData eventData(String caseName) {
        return TestDataSurface.API.getCaseData(caseName, EventPayloadTestCase.class);
    }

    private static BookingApiData bookingData(String caseName) {
        return TestDataSurface.API.getCaseData(caseName, BookingApiTestCase.class);
    }

    private static CreateEventRequest toEventRequest(String title, EventPayloadData data) {
        String eventDate = data.eventDate() != null ? data.eventDate() : DateUtils.futureIsoDate(data.daysInFuture());
        return new CreateEventRequest(title, data.eventDescription(), data.category(), data.venue(), data.city(),
                eventDate, data.price(), data.totalSeats(), data.imageUrl());
    }

    // ---------------------------------------------------------------- full lifecycle

    @Given("I register a brand-new fully isolated account using the {string} auth test data")
    public void iRegisterABrandNewFullyIsolatedAccount(String caseName) {
        AuthApiData registration = TestDataSurface.API.getCaseData(caseName, AuthApiTestCase.class);
        context.authService.register(RandomDataUtils.uniqueEmail("e2e.flow"), registration.password());
    }

    @When("I create an e2e event titled {string} from the {string} event test data")
    public void iCreateAnE2eEventTitled(String title, String caseName) {
        lastEventRequest = toEventRequest(title + " " + RandomDataUtils.uniqueId(), eventData(caseName));
        response = context.eventService.createEvent(lastEventRequest);
        if (response.statusCode() == 201) {
            lastEventId = response.jsonPath().getInt("data.id");
            context.createdEventId = lastEventId;
        }
    }

    @Then("the e2e create-event response should match the {string} event test data's expected status code")
    public void theE2eCreateEventResponseShouldMatchExpectedStatusCode(String caseName) {
        response.assertStatusCode(eventData(caseName).expectedStatusCode());
    }

    @And("the created event should surface via a direct GET and via a search for {string}")
    public void theCreatedEventShouldSurfaceViaAGetAndASearch(String searchTerm) {
        context.eventService.getEvent(lastEventId).assertStatusCode(200);
        List<String> searchResults = context.eventService.listEvents(java.util.Map.of("search", searchTerm))
                .jsonPath().getList("data.title", String.class);
        assertTrue(searchResults.contains(lastEventRequest.title()), "New event should be findable via search: " + searchResults);
    }

    @When("I update the e2e event to {string} using the {string} event test data")
    public void iUpdateTheE2eEventTo(String newTitle, String caseName) {
        EventPayloadData updateData = eventData(caseName);
        lastEventRequest = toEventRequest(newTitle, updateData);
        response = context.eventService.updateEvent(lastEventId, lastEventRequest);
    }

    @Then("the e2e update should be durable per the {string} event test data")
    public void theE2eUpdateShouldBeDurable(String caseName) {
        EventPayloadData updateData = eventData(caseName);
        response.assertStatusCode(updateData.expectedStatusCode());
        // A fresh GET, not just the PUT's own echo.
        assertEquals(context.eventService.getEvent(lastEventId).jsonPath().getString("data.venue"), updateData.venue());
    }

    @When("I book the e2e event using the {string} booking test data")
    public void iBookTheE2eEventUsing(String caseName) {
        BookingApiData caseData = bookingData(caseName);
        CreateBookingRequest bookingRequest = new CreateBookingRequest(
                lastEventId, caseData.customerName(), RandomDataUtils.uniqueEmail("e2e.customer"), caseData.customerPhone(), caseData.quantity());
        response = context.bookingService.createBooking(bookingRequest);
        lastBookingId = response.jsonPath().getInt("data.id");
        context.createdBookingId = lastBookingId;
    }

    @Then("the e2e booking should reference the event and be confirmed per the {string} booking test data")
    public void theE2eBookingShouldReferenceTheEvent(String caseName) {
        BookingApiData caseData = bookingData(caseName);
        response.assertStatusCode(caseData.expectedStatusCode());
        BookingResponse booking = response.extract("data", BookingResponse.class);
        assertEquals(booking.eventId(), lastEventId, "Booking should reference the event it was made against.");
        assertEquals(booking.status(), caseData.expectedBookingStatus(), "A freshly created booking should be confirmed.");
    }

    @And("the e2e event's available seats should reflect the {string} event test data's seats minus the {string} booking test data's quantity")
    public void theE2eEventsAvailableSeatsShouldReflect(String eventCaseName, String bookingCaseName) {
        EventPayloadData eventCaseData = eventData(eventCaseName);
        BookingApiData bookingCaseData = bookingData(bookingCaseName);
        // The booking response's own nested "event" is a stale pre-transaction snapshot
        // (verified live), so a fresh GET is required.
        EventResponse afterBooking = context.eventService.getEvent(lastEventId).extract("data", EventResponse.class);
        int expectedAvailable = eventCaseData.totalSeats() - bookingCaseData.quantity();
        assertEquals(afterBooking.availableSeats(), expectedAvailable,
                eventCaseData.totalSeats() + " total - " + bookingCaseData.quantity() + " booked should leave " + expectedAvailable + " available.");
    }

    @And("the e2e booking should be findable by id, by reference, and in its event's booking list")
    public void theE2eBookingShouldBeFindable() {
        BookingResponse byId = context.bookingService.getBooking(lastBookingId).extract("data", BookingResponse.class);
        BookingResponse byRef = context.bookingService.getBookingByReference(byId.bookingRef()).extract("data", BookingResponse.class);
        assertEquals(byRef.id(), lastBookingId, "Booking fetched by reference should resolve back to the same booking id.");

        List<Integer> bookingIdsForEvent = context.bookingService.listBookingsForEvent(lastEventId).jsonPath().getList("data.id", Integer.class);
        assertTrue(bookingIdsForEvent.contains(lastBookingId), "Event-scoped booking list should include it: " + bookingIdsForEvent);
    }

    @When("I cancel the e2e booking")
    public void iCancelTheE2eBooking() {
        context.bookingService.cancelBooking(lastBookingId).assertStatusCode(200);
    }

    @Then("cancelling should restore the {string} event test data's total seats and the booking should now return 404")
    public void cancellingShouldRestoreTotalSeatsAndBookingShouldReturn404(String caseName) {
        EventPayloadData caseData = eventData(caseName);
        assertEquals(context.eventService.getEvent(lastEventId).jsonPath().getInt("data.availableSeats"), caseData.totalSeats(),
                "Cancelling should restore every seat.");
        context.bookingService.getBooking(lastBookingId).assertStatusCode(404);
        context.createdBookingId = null; // already gone - nothing left for ApiHooks to cancel.
    }

    @When("I delete the e2e event")
    public void iDeleteTheE2eEvent() {
        context.eventService.deleteEvent(lastEventId).assertStatusCode(200);
        context.createdEventId = null; // already gone - nothing left for ApiHooks to delete.
    }

    @Then("the e2e event should now return 404")
    public void theE2eEventShouldNowReturn404() {
        context.eventService.getEvent(lastEventId).assertStatusCode(404);
    }

    @Then("the cascade-deleted booking should now return 404")
    public void theCascadeDeletedBookingShouldNowReturn404() {
        // Nothing left for ApiHooks to cancel - the cascade already removed it.
        context.bookingService.getBooking(lastBookingId).assertStatusCode(404);
        context.createdBookingId = null;
    }

    // ---------------------------------------------------------------- ApiContext chaining

    @Then("ApiContext should already hold the access token")
    public void apiContextShouldAlreadyHoldTheAccessToken() {
        assertTrue(ApiContext.has(ApiContext.ACCESS_TOKEN_KEY), "Login should have populated the ApiContext access token.");
    }

    @And("the created event id should be chained through ApiContext")
    public void theCreatedEventIdShouldBeChainedThroughApiContext() {
        ApiContext.set("eventId", String.valueOf(lastEventId));
    }

    @When("I book the event id chained through ApiContext using the {string} booking test data")
    public void iBookTheEventIdChainedThroughApiContext(String caseName) {
        BookingApiData caseData = bookingData(caseName);
        CreateBookingRequest bookingRequest = new CreateBookingRequest(
                Integer.parseInt(ApiContext.get("eventId")), caseData.customerName(), RandomDataUtils.uniqueEmail("context.tester"),
                caseData.customerPhone(), caseData.quantity());
        response = context.bookingService.createBooking(bookingRequest);
        response.assertStatusCode(caseData.expectedStatusCode());
        lastBookingId = response.jsonPath().getInt("data.id");
        context.createdBookingId = lastBookingId;
    }

    @Then("the e2e booking should reference the event id chained through ApiContext")
    public void theE2eBookingShouldReferenceTheEventIdChainedThroughApiContext() {
        BookingResponse booking = response.extract("data", BookingResponse.class);
        assertEquals(String.valueOf(booking.eventId()), ApiContext.get("eventId"),
                "Booking should reference the event ID chained through ApiContext.");
    }

    // ---------------------------------------------------------------- sequential bookings

    @And("I make the first sequential booking using the {string} booking test data")
    public void iMakeTheFirstSequentialBooking(String caseName) {
        BookingApiData firstBookingData = bookingData(caseName);
        firstBookingQuantity = firstBookingData.quantity();
        firstBookingId = context.bookingService.createBooking(
                        new CreateBookingRequest(lastEventId, firstBookingData.customerName(), RandomDataUtils.uniqueEmail("buyer.one"),
                                firstBookingData.customerPhone(), firstBookingData.quantity()))
                .jsonPath().getInt("data.id");
        afterFirstBooking = eventData("e2eSequentialBookingsEvent").totalSeats() - firstBookingData.quantity();
    }

    @Then("the available seats after the first booking should be correct")
    public void theAvailableSeatsAfterTheFirstBookingShouldBeCorrect() {
        assertEquals(context.eventService.getEvent(lastEventId).jsonPath().getInt("data.availableSeats"), afterFirstBooking);
    }

    @When("I make the second sequential booking using the {string} booking test data")
    public void iMakeTheSecondSequentialBooking(String caseName) {
        BookingApiData secondBookingData = bookingData(caseName);
        int secondBookingId = context.bookingService.createBooking(
                        new CreateBookingRequest(lastEventId, secondBookingData.customerName(), RandomDataUtils.uniqueEmail("buyer.two"),
                                secondBookingData.customerPhone(), secondBookingData.quantity()))
                .jsonPath().getInt("data.id");
        afterSecondBooking = afterFirstBooking - secondBookingData.quantity();
        context.createdBookingId = secondBookingId; // the only one still standing - ApiHooks cancels it.
    }

    @Then("the available seats after the second booking should be correct")
    public void theAvailableSeatsAfterTheSecondBookingShouldBeCorrect() {
        assertEquals(context.eventService.getEvent(lastEventId).jsonPath().getInt("data.availableSeats"), afterSecondBooking);
    }

    @When("I cancel the first sequential booking")
    public void iCancelTheFirstSequentialBooking() {
        context.bookingService.cancelBooking(firstBookingId).assertStatusCode(200);
    }

    @Then("cancelling the first booking should restore exactly its own seats")
    public void cancellingTheFirstBookingShouldRestoreExactlyItsOwnSeats() {
        assertEquals(context.eventService.getEvent(lastEventId).jsonPath().getInt("data.availableSeats"), afterSecondBooking + firstBookingQuantity,
                "Cancelling the " + firstBookingQuantity + "-seat booking should restore just those " + firstBookingQuantity + ".");
    }
}
