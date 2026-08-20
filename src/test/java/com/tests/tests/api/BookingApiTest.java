package com.tests.tests.api;

import com.framework.api.ApiResponse;
import com.tests.application.requests.CreateBookingRequest;
import com.tests.application.requests.CreateEventRequest;
import com.tests.application.responses.BookingResponse;
import com.tests.application.responses.EventResponse;
import com.tests.application.services.BookingService;
import com.tests.application.services.EventService;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.BookingApiTestCase.BookingApiData;
import com.tests.application.testdata.api.BookingApiTestCase;
import com.framework.utils.DateUtils;
import com.framework.utils.RandomDataUtils;
import com.tests.application.base.BaseApiTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertNotNull;
import static com.framework.utils.Verify.assertTrue;

/**
 * Full positive/negative coverage of eventhub's {@code /bookings} endpoints, against the live
 * API. Every test provisions its own throwaway event (small {@code totalSeats}, so
 * overbooking/insufficient-seats scenarios are cheap to trigger deterministically) and tears
 * down both the booking and the event in {@link #tearDownTestData()}.
 *
 * <p>Test data (metadata/data per case, for easy identification in a failure or a report)
 * lives in {@code testdata/json/api/api.json}, separate from {@code testdata/json/web/web.json}
 * (Web) and {@code testdata/json/android/android.json}/{@code testdata/json/ios/ios.json} (Mobile) -
 * "maintain separate files per surface" per the task this suite was written for.</p>
 */
public class BookingApiTest extends BaseApiTest {

    private final EventService eventService = new EventService();
    private final BookingService bookingService = new BookingService();

    // ThreadLocal, not a plain field (audit finding, verified live with -Dparallel=methods
    // -DthreadCount=8): TestNG runs every @Test method of a class on one shared instance under
    // method-level parallelism, not one instance per thread/method - a plain field here let one
    // thread's write clobber another's, so tearDownTestData() could cancel/delete a different
    // thread's still-in-use booking/event. Same reasoning as com.framework.api.ApiContext/
    // ConfigManager's own thread-local tiers.
    private final ThreadLocal<Integer> createdEventId = new ThreadLocal<>();
    private final ThreadLocal<Integer> createdBookingId = new ThreadLocal<>();

    @BeforeMethod(alwaysRun = true)
    public void logIn() {
        loginWithSeededAccount();
    }

    @Override
    protected void tearDownTestData() {
        if (createdBookingId.get() != null) {
            bookingService.cancelBooking(createdBookingId.get());
            createdBookingId.remove();
        }
        if (createdEventId.get() != null) {
            eventService.deleteEvent(createdEventId.get());
            createdEventId.remove();
        }
    }

    private int createEventWithSeats(int totalSeats) {
        CreateEventRequest request = new CreateEventRequest(
                "Booking Test Event " + RandomDataUtils.uniqueId(), "Workshop", "Venue", "City",
                DateUtils.futureIsoDate(30), 50.0, totalSeats);
        int id = eventService.createEvent(request).jsonPath().getInt("data.id");
        createdEventId.set(id);
        return id;
    }

    private CreateBookingRequest validBookingFor(int eventId, int quantity) {
        BookingApiData data = TestDataSurface.API.getCaseData("defaultBooking", BookingApiTestCase.class);
        return new CreateBookingRequest(eventId, data.customerName(), RandomDataUtils.uniqueEmail("booking.tester"), data.customerPhone(), quantity);
    }

    // ---------------------------------------------------------------- create: positive

    @Test(groups = {"smoke", "api", "bookings", "positive"})
    public void bookingTicketsDecrementsAvailableSeatsAndReturnsABookingRef() {
        BookingApiData data = TestDataSurface.API.getCaseData("standardBookingScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));
        response.assertStatusCode(data.expectedStatusCode());

        BookingResponse booking = response.extract("data", BookingResponse.class);
        createdBookingId.set(booking.id());
        assertEquals(booking.eventId(), eventId, "Booking should reference the event it was made against.");
        assertEquals(booking.quantity(), data.quantity().intValue(), "Booking should record the requested ticket quantity.");
        assertEquals(booking.status(), data.expectedBookingStatus(), "A freshly created booking should be confirmed.");
        assertNotNull(booking.bookingRef(), "Booking should be issued a server-generated reference code.");
        assertTrue(booking.bookingRef().length() > 0, "Booking reference should not be an empty string.");

        // The nested "event" snapshot on the booking response is pre-transaction and stale
        // (verified live) - a fresh GET is required to observe the atomic seat decrement.
        EventResponse eventAfterBooking = eventService.getEvent(eventId).extract("data", EventResponse.class);
        assertEquals(eventAfterBooking.availableSeats(), data.totalSeats() - data.quantity(),
                data.totalSeats() + " total - " + data.quantity() + " booked should leave " + (data.totalSeats() - data.quantity()) + " available.");
    }

    @Test(groups = {"smoke", "api", "bookings", "positive"})
    public void bookingExactlyAllRemainingSeatsSucceeds() {
        BookingApiData data = TestDataSurface.API.getCaseData("exactRemainingSeatsScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));

        response.assertStatusCode(data.expectedStatusCode());
        createdBookingId.set(response.jsonPath().getInt("data.id"));
        EventResponse afterBooking = eventService.getEvent(eventId).extract("data", EventResponse.class);
        assertEquals(afterBooking.availableSeats(), 0, "Booking every remaining seat should leave none available.");
    }

    // ---------------------------------------------------------------- create: negative

    @Test(groups = {"api", "bookings", "negative"})
    public void bookingMoreTicketsThanAvailableSeatsFails() {
        BookingApiData data = TestDataSurface.API.getCaseData("overbookingScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"),
                "Only " + data.totalSeats() + " seat(s) available, but " + data.quantity() + " requested",
                "Overbooking error message should state the actual seat shortfall.");
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void bookingForANonexistentEventReturns404() {
        BookingApiData data = TestDataSurface.API.getCaseData("nonexistentEventBooking", BookingApiTestCase.class);
        ApiResponse response = bookingService.createBooking(validBookingFor(999_999, 1));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), data.expectedError(),
                "Booking a non-existent event should report which event id was not found.");
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void bookingWithQuantityZeroFailsValidation() {
        BookingApiData data = TestDataSurface.API.getCaseData("zeroQuantityScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("details[0].field"), data.expectedField(),
                "Validation error should flag 'quantity' as the invalid field.");
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void bookingWithQuantityAboveTenFailsValidation() {
        BookingApiData data = TestDataSurface.API.getCaseData("aboveMaxQuantityScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("details[0].message"), data.expectedMessage(),
                "Validation error should explain the documented 1-10 quantity range.");
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void bookingWithAnInvalidCustomerEmailFailsValidation() {
        BookingApiData data = TestDataSurface.API.getCaseData("invalidCustomerEmail", BookingApiTestCase.class);
        int eventId = createEventWithSeats(5);
        CreateBookingRequest request = new CreateBookingRequest(eventId, data.customerName(), data.customerEmail(), data.customerPhone(), data.quantity());

        ApiResponse response = bookingService.createBooking(request);

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("details[0].field"), data.expectedField(),
                "Validation error should flag 'customerEmail' as the invalid field.");
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void bookingWithATooShortCustomerPhoneFailsValidation() {
        BookingApiData data = TestDataSurface.API.getCaseData("tooShortCustomerPhone", BookingApiTestCase.class);
        int eventId = createEventWithSeats(5);
        CreateBookingRequest request = new CreateBookingRequest(eventId, data.customerName(), data.customerEmail(), data.customerPhone(), data.quantity());

        ApiResponse response = bookingService.createBooking(request);

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("details[0].field"), data.expectedField(),
                "Validation error should flag 'customerPhone' as the invalid field.");
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void bookingWithoutAuthReturns401() {
        authService.logout();

        ApiResponse response = bookingService.createBooking(validBookingFor(1, 1));

        response.assertStatusCode(401);
    }

    // ---------------------------------------------------------------- get by id / by reference

    @Test(groups = {"smoke", "api", "bookings", "positive"})
    public void gettingABookingByIdAndByReferenceReturnTheSameBooking() {
        int eventId = createEventWithSeats(5);
        BookingResponse created = bookingService.createBooking(validBookingFor(eventId, 1)).extract("data", BookingResponse.class);
        createdBookingId.set(created.id());

        BookingResponse byId = bookingService.getBooking(created.id()).extract("data", BookingResponse.class);
        BookingResponse byRef = bookingService.getBookingByReference(created.bookingRef()).extract("data", BookingResponse.class);

        assertEquals(byId.bookingRef(), created.bookingRef(), "Booking fetched by id should carry the same reference code it was created with.");
        assertEquals(byRef.id(), created.id(), "Booking fetched by reference should resolve back to the same booking id.");
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void gettingANonexistentBookingByIdReturns404() {
        BookingApiData data = TestDataSurface.API.getCaseData("nonexistentBookingLookup", BookingApiTestCase.class);
        ApiResponse response = bookingService.getBooking(999_999);

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), data.expectedError(),
                "Looking up a non-existent booking should report which booking id was not found.");
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void gettingANonexistentBookingByReferenceReturns404() {
        BookingApiData data = TestDataSurface.API.getCaseData("nonexistentBookingReference", BookingApiTestCase.class);
        ApiResponse response = bookingService.getBookingByReference(data.bookingReference());

        response.assertStatusCode(data.expectedStatusCode());
        assertTrue(response.jsonPath().getString("error").contains(data.bookingReference()),
                "Not-found error should echo back the reference code that wasn't found.");
    }

    // ---------------------------------------------------------------- list

    @Test(groups = {"smoke", "api", "bookings", "positive"})
    public void listingBookingsFilteredByEventIdReturnsOnlyThatEventsBookings() {
        int eventId = createEventWithSeats(5);
        BookingResponse created = bookingService.createBooking(validBookingFor(eventId, 1)).extract("data", BookingResponse.class);
        createdBookingId.set(created.id());

        ApiResponse response = bookingService.listBookingsForEvent(eventId);

        response.assertStatusCode(200);
        List<Integer> eventIds = response.jsonPath().getList("data.eventId", Integer.class);
        assertTrue(eventIds.stream().allMatch(id -> id == eventId), "Every result should reference event " + eventId + ": " + eventIds);
    }

    @Test(groups = {"api", "bookings", "positive"})
    public void listingBookingsRespectsPagination() {
        BookingApiData data = TestDataSurface.API.getCaseData("pageableListingScenario", BookingApiTestCase.class);
        ApiResponse response = bookingService.listBookings(Map.of("page", data.page(), "limit", data.limit()));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getInt("pagination.page"), data.page().intValue(), "Response pagination should echo back the requested page.");
        assertEquals(response.jsonPath().getInt("pagination.limit"), data.limit().intValue(), "Response pagination should echo back the requested limit.");
        assertTrue(response.jsonPath().getList("data").size() <= data.limit(), "Result count should not exceed the requested page limit.");
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void listingBookingsWithoutAuthReturns401() {
        authService.logout();

        ApiResponse response = bookingService.listBookings();

        response.assertStatusCode(401);
    }

    // ---------------------------------------------------------------- cancel

    @Test(groups = {"smoke", "api", "bookings", "positive"})
    public void cancellingABookingRestoresTheSeatAndMakesItUnretrievable() {
        BookingApiData data = TestDataSurface.API.getCaseData("bookingCancellation", BookingApiTestCase.class);
        int eventId = createEventWithSeats(3);
        int bookingId = bookingService.createBooking(validBookingFor(eventId, 2)).jsonPath().getInt("data.id");
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), 1, "Booking 2 of 3 seats should leave 1 available.");

        ApiResponse cancelResponse = bookingService.cancelBooking(bookingId);

        cancelResponse.assertStatusCode(data.expectedStatusCode());
        assertEquals(cancelResponse.jsonPath().getString("message"), data.expectedMessage(),
                "Cancellation response should confirm the booking was cancelled.");
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), 3, "Cancelling should restore all 2 seats.");

        // Cancelling deletes the row outright (verified live) rather than flagging it
        // "cancelled" - a subsequent GET 404s, so nothing is left for cleanup() to cancel again.
        bookingService.getBooking(bookingId).assertStatusCode(404);
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void cancellingAnAlreadyCancelledBookingReturns404OnTheSecondCall() {
        int eventId = createEventWithSeats(3);
        int bookingId = bookingService.createBooking(validBookingFor(eventId, 1)).jsonPath().getInt("data.id");
        bookingService.cancelBooking(bookingId).assertStatusCode(200);

        ApiResponse secondCancel = bookingService.cancelBooking(bookingId);

        secondCancel.assertStatusCode(404);
    }

    @Test(groups = {"api", "bookings", "negative"})
    public void cancellingANonexistentBookingReturns404() {
        ApiResponse response = bookingService.cancelBooking(999_999);

        response.assertStatusCode(404);
    }
}
