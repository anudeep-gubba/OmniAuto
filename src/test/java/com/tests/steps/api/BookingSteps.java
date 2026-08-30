package com.tests.steps.api;

import com.framework.api.ApiResponse;
import com.framework.utils.DateUtils;
import com.framework.utils.RandomDataUtils;
import com.tests.application.requests.CreateBookingRequest;
import com.tests.application.requests.CreateEventRequest;
import com.tests.application.responses.BookingResponse;
import com.tests.application.responses.EventResponse;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.BookingApiTestCase;
import com.tests.application.testdata.api.BookingApiTestCase.BookingApiData;
import com.tests.steps.shared.ApiScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertNotNull;
import static com.framework.utils.Verify.assertTrue;

/**
 * Steps behind {@code features/api/bookings.feature} - a mechanical lift of the old
 * {@code com.tests.tests.api.BookingApiTest} {@code @Test} method bodies into Given/When/Then
 * steps; every service call and assertion is unchanged.
 */
public class BookingSteps {

    private final ApiScenarioContext context;

    private ApiResponse response;
    private int lastEventId;
    private int lastBookingId;
    private BookingResponse createdBooking;

    public BookingSteps(ApiScenarioContext context) {
        this.context = context;
    }

    private static BookingApiData data(String caseName) {
        return TestDataSurface.API.getCaseData(caseName, BookingApiTestCase.class);
    }

    private int createEventWithSeats(int totalSeats) {
        CreateEventRequest request = new CreateEventRequest(
                "Booking Test Event " + RandomDataUtils.uniqueId(), "Workshop", "Venue", "City",
                DateUtils.futureIsoDate(30), 50.0, totalSeats);
        int id = context.eventService.createEvent(request).jsonPath().getInt("data.id");
        context.createdEventId = id;
        return id;
    }

    private static CreateBookingRequest validBookingFor(int eventId, int quantity) {
        BookingApiData defaultData = data("defaultBooking");
        return new CreateBookingRequest(eventId, defaultData.customerName(), RandomDataUtils.uniqueEmail("booking.tester"), defaultData.customerPhone(), quantity);
    }

    // ---------------------------------------------------------------- create

    @When("I create an event and book it using the {string} booking test data")
    public void iCreateAnEventAndBookItUsing(String caseName) {
        BookingApiData caseData = data(caseName);
        lastEventId = createEventWithSeats(caseData.totalSeats());
        response = context.bookingService.createBooking(validBookingFor(lastEventId, caseData.quantity()));
    }

    @Given("I create a throwaway event with {int} seats")
    public void iCreateAThrowawayEventWithSeats(int totalSeats) {
        lastEventId = createEventWithSeats(totalSeats);
    }

    @When("I book that event using the {string} booking test data's own customer details")
    public void iBookThatEventUsingItsOwnCustomerDetails(String caseName) {
        BookingApiData caseData = data(caseName);
        CreateBookingRequest request = new CreateBookingRequest(lastEventId, caseData.customerName(), caseData.customerEmail(), caseData.customerPhone(), caseData.quantity());
        response = context.bookingService.createBooking(request);
    }

    @When("I book that event with quantity {int} using the default customer details")
    public void iBookThatEventWithQuantity(int quantity) {
        response = context.bookingService.createBooking(validBookingFor(lastEventId, quantity));
        if (response.statusCode() == 201) {
            lastBookingId = response.jsonPath().getInt("data.id");
        }
    }

    @When("I book event id {int} with quantity {int} using the default customer details")
    public void iBookEventIdWithQuantity(int eventId, int quantity) {
        response = context.bookingService.createBooking(validBookingFor(eventId, quantity));
    }

    @And("I track the booking for cleanup")
    public void iTrackTheBookingForCleanup() {
        lastBookingId = response.jsonPath().getInt("data.id");
        context.createdBookingId = lastBookingId;
    }

    @Then("the created booking should reference the event, record the quantity, and be confirmed per the {string} booking test data")
    public void theCreatedBookingShouldReferenceTheEvent(String caseName) {
        BookingApiData caseData = data(caseName);
        createdBooking = response.extract("data", BookingResponse.class);
        lastBookingId = createdBooking.id();
        context.createdBookingId = lastBookingId;
        assertEquals(createdBooking.eventId(), lastEventId, "Booking should reference the event it was made against.");
        assertEquals(createdBooking.quantity(), caseData.quantity().intValue(), "Booking should record the requested ticket quantity.");
        assertEquals(createdBooking.status(), caseData.expectedBookingStatus(), "A freshly created booking should be confirmed.");
        assertNotNull(createdBooking.bookingRef(), "Booking should be issued a server-generated reference code.");
        assertTrue(createdBooking.bookingRef().length() > 0, "Booking reference should not be an empty string.");
    }

    @And("the event's available seats should reflect the {string} booking test data's seat decrement")
    public void theEventsAvailableSeatsShouldReflectTheSeatDecrement(String caseName) {
        BookingApiData caseData = data(caseName);
        int totalSeats = caseData.totalSeats();
        // The nested "event" snapshot on the booking response is pre-transaction and stale
        // (verified live) - a fresh GET is required to observe the atomic seat decrement.
        EventResponse eventAfterBooking = context.eventService.getEvent(lastEventId).extract("data", EventResponse.class);
        assertEquals(eventAfterBooking.availableSeats(), totalSeats - caseData.quantity(),
                totalSeats + " total - " + caseData.quantity() + " booked should leave " + (totalSeats - caseData.quantity()) + " available.");
    }

    @And("the event should have zero available seats remaining")
    public void theEventShouldHaveZeroAvailableSeatsRemaining() {
        EventResponse afterBooking = context.eventService.getEvent(lastEventId).extract("data", EventResponse.class);
        assertEquals(afterBooking.availableSeats(), 0, "Booking every remaining seat should leave none available.");
    }

    @And("the event should have {int} available seat(s) remaining")
    public void theEventShouldHaveAvailableSeatsRemaining(int expectedAvailable) {
        assertEquals(context.eventService.getEvent(lastEventId).jsonPath().getInt("data.availableSeats"), expectedAvailable);
    }

    @And("the overbooking error message should state the actual seat shortfall per the {string} booking test data")
    public void theOverbookingErrorMessageShouldStateTheShortfall(String caseName) {
        BookingApiData caseData = data(caseName);
        assertEquals(response.jsonPath().getString("error"),
                "Only " + caseData.totalSeats() + " seat(s) available, but " + caseData.quantity() + " requested",
                "Overbooking error message should state the actual seat shortfall.");
    }

    @And("the booking validation error should flag the {string} booking test data's expected field")
    public void theBookingValidationErrorShouldFlagExpectedField(String caseName) {
        assertEquals(response.jsonPath().getString("details[0].field"), data(caseName).expectedField(),
                "Validation error should flag '" + data(caseName).expectedField() + "' as the invalid field.");
    }

    @And("the booking validation error should match the {string} booking test data's expected message")
    public void theBookingValidationErrorShouldMatchExpectedMessage(String caseName) {
        assertEquals(response.jsonPath().getString("details[0].message"), data(caseName).expectedMessage(),
                "Validation error should explain the documented 1-10 quantity range.");
    }

    // ---------------------------------------------------------------- get

    @When("I get booking id {int}")
    public void iGetBookingId(int bookingId) {
        response = context.bookingService.getBooking(bookingId);
    }

    @When("I get the booking by the {string} booking test data's reference")
    public void iGetTheBookingByReference(String caseName) {
        response = context.bookingService.getBookingByReference(data(caseName).bookingReference());
    }

    @Then("getting the booking by id and by reference should return the same booking")
    public void gettingTheBookingByIdAndByReferenceShouldReturnTheSameBooking() {
        BookingResponse byId = context.bookingService.getBooking(lastBookingId).extract("data", BookingResponse.class);
        BookingResponse byRef = context.bookingService.getBookingByReference(byId.bookingRef()).extract("data", BookingResponse.class);
        assertEquals(byRef.id(), lastBookingId, "Booking fetched by reference should resolve back to the same booking id.");
    }

    @And("the not-found error should echo back the {string} booking test data's reference")
    public void theNotFoundErrorShouldEchoBackTheReference(String caseName) {
        String reference = data(caseName).bookingReference();
        assertTrue(response.jsonPath().getString("error").contains(reference),
                "Not-found error should echo back the reference code that wasn't found.");
    }

    // ---------------------------------------------------------------- list

    @And("I list bookings filtered by that event's id")
    public void iListBookingsFilteredByThatEventsId() {
        response = context.bookingService.listBookingsForEvent(lastEventId);
    }

    @And("every listed booking should reference that event")
    public void everyListedBookingShouldReferenceThatEvent() {
        List<Integer> eventIds = response.jsonPath().getList("data.eventId", Integer.class);
        assertTrue(eventIds.stream().allMatch(id -> id == lastEventId), "Every result should reference event " + lastEventId + ": " + eventIds);
    }

    @When("I list bookings using the {string} booking test data's page and limit")
    public void iListBookingsUsingPageAndLimit(String caseName) {
        BookingApiData caseData = data(caseName);
        response = context.bookingService.listBookings(Map.of("page", caseData.page(), "limit", caseData.limit()));
    }

    @And("the booking list pagination should echo back the {string} booking test data's page and limit")
    public void theBookingListPaginationShouldEchoBack(String caseName) {
        BookingApiData caseData = data(caseName);
        assertEquals(response.jsonPath().getInt("pagination.page"), caseData.page().intValue(), "Response pagination should echo back the requested page.");
        assertEquals(response.jsonPath().getInt("pagination.limit"), caseData.limit().intValue(), "Response pagination should echo back the requested limit.");
        assertTrue(response.jsonPath().getList("data").size() <= caseData.limit(), "Result count should not exceed the requested page limit.");
    }

    @When("I list all bookings")
    public void iListAllBookings() {
        response = context.bookingService.listBookings();
    }

    // ---------------------------------------------------------------- cancel

    @When("I cancel that booking")
    public void iCancelThatBooking() {
        response = context.bookingService.cancelBooking(lastBookingId);
    }

    @When("I cancel that booking again")
    public void iCancelThatBookingAgain() {
        response = context.bookingService.cancelBooking(lastBookingId);
    }

    @When("I cancel booking id {int}")
    public void iCancelBookingId(int bookingId) {
        response = context.bookingService.cancelBooking(bookingId);
    }

    @Then("the cancel-booking response should match the {string} booking test data's expected status and message")
    public void theCancelBookingResponseShouldMatchExpectedStatusAndMessage(String caseName) {
        BookingApiData caseData = data(caseName);
        response.assertStatusCode(caseData.expectedStatusCode());
        assertEquals(response.jsonPath().getString("message"), caseData.expectedMessage(),
                "Cancellation response should confirm the booking was cancelled.");
    }

    @Then("that booking should now return 404")
    public void thatBookingShouldNowReturn404() {
        // Cancelling deletes the row outright (verified live) rather than flagging it
        // "cancelled" - a subsequent GET 404s, so nothing is left for cleanup to cancel again.
        context.bookingService.getBooking(lastBookingId).assertStatusCode(404);
    }

    // ---------------------------------------------------------------- shared response assertions

    @Then("the booking response should match the {string} booking test data's expected status code")
    public void theBookingResponseShouldMatchExpectedStatusCode(String caseName) {
        response.assertStatusCode(data(caseName).expectedStatusCode());
    }

    @Then("the booking response should match the {string} booking test data's expected status and error")
    public void theBookingResponseShouldMatchExpectedStatusAndError(String caseName) {
        BookingApiData caseData = data(caseName);
        response.assertStatusCode(caseData.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), caseData.expectedError(),
                "Booking a non-existent event should report which event id was not found.");
    }

    @Then("the booking response status code should be {int}")
    public void theBookingResponseStatusCodeShouldBe(int statusCode) {
        response.assertStatusCode(statusCode);
    }
}
