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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

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

    private Integer createdEventId;
    private Integer createdBookingId;

    @BeforeMethod(alwaysRun = true)
    public void logIn() {
        loginWithSeededAccount();
    }

    @Override
    protected void tearDownTestData() {
        if (createdBookingId != null) {
            bookingService.cancelBooking(createdBookingId);
            createdBookingId = null;
        }
        if (createdEventId != null) {
            eventService.deleteEvent(createdEventId);
            createdEventId = null;
        }
    }

    private int createEventWithSeats(int totalSeats) {
        CreateEventRequest request = new CreateEventRequest(
                "Booking Test Event " + RandomDataUtils.uniqueId(), "Workshop", "Venue", "City",
                DateUtils.futureIsoDate(30), 50.0, totalSeats);
        int id = eventService.createEvent(request).jsonPath().getInt("data.id");
        createdEventId = id;
        return id;
    }

    private CreateBookingRequest validBookingFor(int eventId, int quantity) {
        BookingApiData data = TestDataSurface.API.getCaseData("defaultBooking", BookingApiTestCase.class);
        return new CreateBookingRequest(eventId, data.customerName(), RandomDataUtils.uniqueEmail("booking.tester"), data.customerPhone(), quantity);
    }

    // ---------------------------------------------------------------- create: positive

    @Test(groups = {"smoke", "api"})
    public void bookingTicketsDecrementsAvailableSeatsAndReturnsABookingRef() {
        BookingApiData data = TestDataSurface.API.getCaseData("standardBookingScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));
        response.assertStatusCode(201);

        BookingResponse booking = response.extract("data", BookingResponse.class);
        createdBookingId = booking.id();
        assertEquals(booking.eventId(), eventId);
        assertEquals(booking.quantity(), data.quantity().intValue());
        assertEquals(booking.status(), "confirmed");
        assertNotNull(booking.bookingRef());
        assertTrue(booking.bookingRef().length() > 0);

        // The nested "event" snapshot on the booking response is pre-transaction and stale
        // (verified live) - a fresh GET is required to observe the atomic seat decrement.
        EventResponse eventAfterBooking = eventService.getEvent(eventId).extract("data", EventResponse.class);
        assertEquals(eventAfterBooking.availableSeats(), data.totalSeats() - data.quantity(),
                data.totalSeats() + " total - " + data.quantity() + " booked should leave " + (data.totalSeats() - data.quantity()) + " available.");
    }

    @Test(groups = {"smoke", "api"})
    public void bookingExactlyAllRemainingSeatsSucceeds() {
        BookingApiData data = TestDataSurface.API.getCaseData("exactRemainingSeatsScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));

        response.assertStatusCode(201);
        createdBookingId = response.jsonPath().getInt("data.id");
        EventResponse afterBooking = eventService.getEvent(eventId).extract("data", EventResponse.class);
        assertEquals(afterBooking.availableSeats(), 0);
    }

    // ---------------------------------------------------------------- create: negative

    @Test(groups = "api")
    public void bookingMoreTicketsThanAvailableSeatsFails() {
        BookingApiData data = TestDataSurface.API.getCaseData("overbookingScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("error"),
                "Only " + data.totalSeats() + " seat(s) available, but " + data.quantity() + " requested");
    }

    @Test(groups = "api")
    public void bookingForANonexistentEventReturns404() {
        ApiResponse response = bookingService.createBooking(validBookingFor(999_999, 1));

        response.assertStatusCode(404);
        assertEquals(response.jsonPath().getString("error"), "Event with id 999999 not found");
    }

    @Test(groups = "api")
    public void bookingWithQuantityZeroFailsValidation() {
        BookingApiData data = TestDataSurface.API.getCaseData("zeroQuantityScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("details[0].field"), "quantity");
    }

    @Test(groups = "api")
    public void bookingWithQuantityAboveTenFailsValidation() {
        BookingApiData data = TestDataSurface.API.getCaseData("aboveMaxQuantityScenario", BookingApiTestCase.class);
        int eventId = createEventWithSeats(data.totalSeats());

        ApiResponse response = bookingService.createBooking(validBookingFor(eventId, data.quantity()));

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("details[0].message"), "Quantity must be an integer between 1 and 10");
    }

    @Test(groups = "api")
    public void bookingWithAnInvalidCustomerEmailFailsValidation() {
        BookingApiData data = TestDataSurface.API.getCaseData("invalidCustomerEmail", BookingApiTestCase.class);
        int eventId = createEventWithSeats(5);
        CreateBookingRequest request = new CreateBookingRequest(eventId, data.customerName(), data.customerEmail(), data.customerPhone(), data.quantity());

        ApiResponse response = bookingService.createBooking(request);

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("details[0].field"), "customerEmail");
    }

    @Test(groups = "api")
    public void bookingWithATooShortCustomerPhoneFailsValidation() {
        BookingApiData data = TestDataSurface.API.getCaseData("tooShortCustomerPhone", BookingApiTestCase.class);
        int eventId = createEventWithSeats(5);
        CreateBookingRequest request = new CreateBookingRequest(eventId, data.customerName(), data.customerEmail(), data.customerPhone(), data.quantity());

        ApiResponse response = bookingService.createBooking(request);

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("details[0].field"), "customerPhone");
    }

    @Test(groups = "api")
    public void bookingWithoutAuthReturns401() {
        authService.logout();

        ApiResponse response = bookingService.createBooking(validBookingFor(1, 1));

        response.assertStatusCode(401);
    }

    // ---------------------------------------------------------------- get by id / by reference

    @Test(groups = {"smoke", "api"})
    public void gettingABookingByIdAndByReferenceReturnTheSameBooking() {
        int eventId = createEventWithSeats(5);
        BookingResponse created = bookingService.createBooking(validBookingFor(eventId, 1)).extract("data", BookingResponse.class);
        createdBookingId = created.id();

        BookingResponse byId = bookingService.getBooking(created.id()).extract("data", BookingResponse.class);
        BookingResponse byRef = bookingService.getBookingByReference(created.bookingRef()).extract("data", BookingResponse.class);

        assertEquals(byId.bookingRef(), created.bookingRef());
        assertEquals(byRef.id(), created.id());
    }

    @Test(groups = "api")
    public void gettingANonexistentBookingByIdReturns404() {
        ApiResponse response = bookingService.getBooking(999_999);

        response.assertStatusCode(404);
        assertEquals(response.jsonPath().getString("error"), "Booking with id 999999 not found");
    }

    @Test(groups = "api")
    public void gettingANonexistentBookingByReferenceReturns404() {
        BookingApiData data = TestDataSurface.API.getCaseData("nonexistentBookingReference", BookingApiTestCase.class);
        ApiResponse response = bookingService.getBookingByReference(data.bookingReference());

        response.assertStatusCode(404);
        assertTrue(response.jsonPath().getString("error").contains(data.bookingReference()));
    }

    // ---------------------------------------------------------------- list

    @Test(groups = {"smoke", "api"})
    public void listingBookingsFilteredByEventIdReturnsOnlyThatEventsBookings() {
        int eventId = createEventWithSeats(5);
        BookingResponse created = bookingService.createBooking(validBookingFor(eventId, 1)).extract("data", BookingResponse.class);
        createdBookingId = created.id();

        ApiResponse response = bookingService.listBookingsForEvent(eventId);

        response.assertStatusCode(200);
        List<Integer> eventIds = response.jsonPath().getList("data.eventId", Integer.class);
        assertTrue(eventIds.stream().allMatch(id -> id == eventId), "Every result should reference event " + eventId + ": " + eventIds);
    }

    @Test(groups = "api")
    public void listingBookingsRespectsPagination() {
        BookingApiData data = TestDataSurface.API.getCaseData("pageableListingScenario", BookingApiTestCase.class);
        ApiResponse response = bookingService.listBookings(Map.of("page", data.page(), "limit", data.limit()));

        response.assertStatusCode(200);
        assertEquals(response.jsonPath().getInt("pagination.page"), data.page().intValue());
        assertEquals(response.jsonPath().getInt("pagination.limit"), data.limit().intValue());
        assertTrue(response.jsonPath().getList("data").size() <= data.limit());
    }

    @Test(groups = "api")
    public void listingBookingsWithoutAuthReturns401() {
        authService.logout();

        ApiResponse response = bookingService.listBookings();

        response.assertStatusCode(401);
    }

    // ---------------------------------------------------------------- cancel

    @Test(groups = {"smoke", "api"})
    public void cancellingABookingRestoresTheSeatAndMakesItUnretrievable() {
        int eventId = createEventWithSeats(3);
        int bookingId = bookingService.createBooking(validBookingFor(eventId, 2)).jsonPath().getInt("data.id");
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), 1);

        ApiResponse cancelResponse = bookingService.cancelBooking(bookingId);

        cancelResponse.assertStatusCode(200);
        assertEquals(cancelResponse.jsonPath().getString("message"), "Booking cancelled");
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), 3, "Cancelling should restore all 2 seats.");

        // Cancelling deletes the row outright (verified live) rather than flagging it
        // "cancelled" - a subsequent GET 404s, so nothing is left for cleanup() to cancel again.
        bookingService.getBooking(bookingId).assertStatusCode(404);
    }

    @Test(groups = "api")
    public void cancellingAnAlreadyCancelledBookingReturns404OnTheSecondCall() {
        int eventId = createEventWithSeats(3);
        int bookingId = bookingService.createBooking(validBookingFor(eventId, 1)).jsonPath().getInt("data.id");
        bookingService.cancelBooking(bookingId).assertStatusCode(200);

        ApiResponse secondCancel = bookingService.cancelBooking(bookingId);

        secondCancel.assertStatusCode(404);
    }

    @Test(groups = "api")
    public void cancellingANonexistentBookingReturns404() {
        ApiResponse response = bookingService.cancelBooking(999_999);

        response.assertStatusCode(404);
    }
}
