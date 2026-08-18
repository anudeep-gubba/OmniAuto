package com.tests.api;

import com.framework.api.ApiResponse;
import com.framework.api.requests.CreateBookingRequest;
import com.framework.api.requests.CreateEventRequest;
import com.framework.api.responses.BookingResponse;
import com.framework.api.responses.EventResponse;
import com.framework.api.services.AuthenticationService;
import com.framework.api.services.BookingService;
import com.framework.api.services.EventService;
import com.framework.secrets.SecretManager;
import com.framework.utils.DateUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Phase 7 validation for requirement.md &sect;11's <b>mandatory</b> API
 * chaining capability, and &sect;38's checklist (create resource, extract
 * ID, use ID in another API), against eventhub's real, persisted API - not a
 * mock. POST /events genuinely creates a database row; the booking genuinely
 * decrements its seat count in the same transaction (per the API's own
 * documented behavior, confirmed live - see Phase 7 summary for how the
 * nested "event" snapshot in a booking response is stale and a fresh
 * {@code GET /events/{id}} is needed to observe it).
 */
public class EventBookingChainingTest {

    private final AuthenticationService authService = new AuthenticationService();
    private final EventService eventService = new EventService();
    private final BookingService bookingService = new BookingService();

    private Integer createdEventId;
    private Integer createdBookingId;

    // alwaysRun = true: found in practice (Phase 13) - TestNG silently skips a @BeforeMethod
    // that lacks this when a group include-filter is active (-Dgroups=smoke), even though the
    // @Test method it authenticates for still runs unauthenticated, producing a confusing 401
    // rather than an obvious "setup didn't run". See CI_CD.md.
    @BeforeMethod(alwaysRun = true)
    public void logIn() {
        authService.login(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        // Real test hygiene against a real, persisted account: undo what this test created.
        if (createdBookingId != null) {
            bookingService.cancelBooking(createdBookingId);
        }
        if (createdEventId != null) {
            eventService.deleteEvent(createdEventId);
        }
        authService.logout();
    }

    @Test(groups = {"smoke", "api"})
    public void creatingAnEventThenBookingItChainsTheExtractedEventId() {
        // 1. Create a resource.
        CreateEventRequest eventRequest = new CreateEventRequest(
                "Framework Chaining Test Event", "Conference", "Test Venue", "Testville",
                DateUtils.futureIsoDate(30), 100.0, 50);
        ApiResponse createEventResponse = eventService.createEvent(eventRequest);
        createEventResponse.assertStatusCode(201);

        // 2. Extract its ID from the response.
        int eventId = createEventResponse.jsonPath().getInt("data.id");
        createdEventId = eventId;
        assertTrue(eventId > 0);

        // 3. Use that extracted ID as input to a second, different API call.
        CreateBookingRequest bookingRequest = new CreateBookingRequest(
                eventId, "Framework Tester", "framework.tester@example.com", "+91-9876500000", 2);
        ApiResponse createBookingResponse = bookingService.createBooking(bookingRequest);
        createBookingResponse.assertStatusCode(201);

        BookingResponse booking = createBookingResponse.extract("data", BookingResponse.class);
        createdBookingId = booking.id();
        assertEquals(booking.eventId(), eventId, "Booking should reference the event just created.");
        assertEquals(booking.quantity(), 2);
        assertEquals(booking.status(), "confirmed");
        assertNotNull(booking.bookingRef());

        // 4. Chain further: verify the created booking two more ways - by ID, and by its
        // server-generated reference code (itself only known because we extracted it above).
        BookingResponse byId = bookingService.getBooking(createdBookingId).extract("data", BookingResponse.class);
        assertEquals(byId.bookingRef(), booking.bookingRef());

        BookingResponse byRef = bookingService.getBookingByReference(booking.bookingRef()).extract("data", BookingResponse.class);
        assertEquals(byRef.id(), createdBookingId.intValue());

        // 5. Confirm the side effect the chain produced: seats atomically decremented.
        // A fresh GET is required - the booking response's own nested "event" is a stale
        // pre-transaction snapshot (verified live, not assumed).
        EventResponse eventAfterBooking = eventService.getEvent(eventId).extract("data", EventResponse.class);
        assertEquals(eventAfterBooking.availableSeats(), 48, "50 total - 2 booked should leave 48 available.");
    }
}
