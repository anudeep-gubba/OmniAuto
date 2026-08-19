package com.tests.api;

import com.framework.api.ApiContext;
import com.framework.api.ApiResponse;
import com.tests.api.requests.CreateBookingRequest;
import com.tests.api.requests.CreateEventRequest;
import com.tests.api.responses.BookingResponse;
import com.tests.api.responses.EventResponse;
import com.tests.api.services.AuthenticationService;
import com.tests.api.services.BookingService;
import com.tests.api.services.EventService;
import com.framework.secrets.SecretManager;
import com.framework.utils.DateUtils;
import com.framework.utils.RandomDataUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Multi-step, cross-resource flows against eventhub's live, persisted API - each one chains a
 * real value extracted from one response (an event ID, a booking reference) into the next
 * request, rather than exercising any single endpoint in isolation. Individual endpoint
 * positive/negative cases live in {@link AuthApiTest}/{@link EventApiTest}/{@link BookingApiTest};
 * this class is specifically about the journeys a real client makes across several of them.
 */
public class EventBookingE2EFlowTest {

    private final AuthenticationService authService = new AuthenticationService();
    private final EventService eventService = new EventService();
    private final BookingService bookingService = new BookingService();

    private Integer createdBookingId;
    private Integer createdEventId;

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        if (createdBookingId != null) {
            bookingService.cancelBooking(createdBookingId);
            createdBookingId = null;
        }
        if (createdEventId != null) {
            eventService.deleteEvent(createdEventId);
            createdEventId = null;
        }
        authService.logout();
        ApiContext.clear();
    }

    /**
     * Registration through cancellation/deletion, on a brand-new account so the flow is fully
     * isolated from the shared seeded account other tests use: register -&gt; create an event -&gt;
     * confirm it surfaces through both a direct GET and a filtered list search -&gt; update it -&gt;
     * book tickets against the freshly-extracted event ID -&gt; confirm the atomic seat decrement
     * with a fresh GET -&gt; look the booking back up by both ID and its server-generated
     * reference -&gt; list bookings scoped to the event -&gt; cancel the booking and confirm the seat
     * is restored and the booking itself is gone -&gt; delete the event and confirm it is gone too.
     */
    @Test(groups = {"smoke", "api"})
    public void fullEventLifecycleFromRegistrationThroughBookingToDeletionWorksEndToEnd() {
        // 1. Register a brand-new, fully isolated account.
        String email = RandomDataUtils.uniqueEmail("e2e.flow");
        authService.register(email, "Framework@2026");

        // 2. Create an event as that account.
        String uniqueTitle = "E2E Flow Event " + RandomDataUtils.uniqueId();
        CreateEventRequest eventRequest = new CreateEventRequest(
                uniqueTitle, "Conference", "Test Venue", "Testville", DateUtils.futureIsoDate(30), 100.0, 10);
        ApiResponse createEventResponse = eventService.createEvent(eventRequest);
        createEventResponse.assertStatusCode(201);
        int eventId = createEventResponse.jsonPath().getInt("data.id");
        createdEventId = eventId;

        // 3. Confirm it surfaces both by direct GET and through a filtered search - two
        // independent read paths onto the same just-created resource.
        eventService.getEvent(eventId).assertStatusCode(200);
        List<String> searchResults = eventService.listEvents(java.util.Map.of("search", "E2E Flow Event"))
                .jsonPath().getList("data.title", String.class);
        assertTrue(searchResults.contains(uniqueTitle), "New event should be findable via search: " + searchResults);

        // 4. Update it, and confirm the change is durable (a fresh GET, not just the PUT's own echo).
        CreateEventRequest updateRequest = new CreateEventRequest(
                uniqueTitle, "Conference", "Updated Venue", "Updated City", DateUtils.futureIsoDate(30), 150.0, 10);
        eventService.updateEvent(eventId, updateRequest).assertStatusCode(200);
        assertEquals(eventService.getEvent(eventId).jsonPath().getString("data.venue"), "Updated Venue");

        // 5. Book tickets, chaining the event ID extracted in step 2 straight into the request.
        CreateBookingRequest bookingRequest = new CreateBookingRequest(
                eventId, "E2E Tester", RandomDataUtils.uniqueEmail("e2e.customer"), "+91-9876500000", 4);
        ApiResponse createBookingResponse = bookingService.createBooking(bookingRequest);
        createBookingResponse.assertStatusCode(201);
        BookingResponse booking = createBookingResponse.extract("data", BookingResponse.class);
        createdBookingId = booking.id();
        assertEquals(booking.eventId(), eventId);
        assertEquals(booking.status(), "confirmed");

        // 6. Confirm the atomic seat decrement - the booking response's own nested "event" is a
        // stale pre-transaction snapshot (verified live), so a fresh GET is required.
        EventResponse afterBooking = eventService.getEvent(eventId).extract("data", EventResponse.class);
        assertEquals(afterBooking.availableSeats(), 6, "10 total - 4 booked should leave 6 available.");

        // 7. Look the booking back up two more ways: by ID, and by its server-generated reference
        // code (itself only known because step 5's response was extracted, not assumed).
        BookingResponse byId = bookingService.getBooking(createdBookingId).extract("data", BookingResponse.class);
        assertEquals(byId.bookingRef(), booking.bookingRef());
        BookingResponse byRef = bookingService.getBookingByReference(booking.bookingRef()).extract("data", BookingResponse.class);
        assertEquals(byRef.id(), createdBookingId.intValue());

        // 8. Confirm the booking shows up filtered by its event.
        List<Integer> bookingIdsForEvent = bookingService.listBookingsForEvent(eventId).jsonPath().getList("data.id", Integer.class);
        assertTrue(bookingIdsForEvent.contains(createdBookingId), "Event-scoped booking list should include it: " + bookingIdsForEvent);

        // 9. Cancel it, and confirm both the seat restoration and that the booking itself is gone.
        bookingService.cancelBooking(createdBookingId).assertStatusCode(200);
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), 10, "Cancelling should restore all 4 seats.");
        bookingService.getBooking(createdBookingId).assertStatusCode(404);
        createdBookingId = null; // already gone - nothing left for cleanup() to cancel.

        // 10. Delete the event, and confirm it is gone too.
        eventService.deleteEvent(eventId).assertStatusCode(200);
        eventService.getEvent(eventId).assertStatusCode(404);
        createdEventId = null; // already gone - nothing left for cleanup() to delete.
    }

    /**
     * The same create-event-then-book chain, but threaded explicitly through
     * {@code ApiContext.set}/{@code get} rather than plain Java locals/fields - proving
     * {@link com.framework.api.ApiClient}'s bearer token (itself stored in {@link ApiContext}
     * under {@link ApiContext#ACCESS_TOKEN_KEY}) and an arbitrary chained value (a booked
     * event's ID) coexist correctly in the same runtime-variable store across a multi-call flow.
     */
    @Test(groups = {"smoke", "api"})
    public void eventIdChainsThroughApiContextIntoTheBookingCall() {
        authService.login(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
        assertTrue(ApiContext.has(ApiContext.ACCESS_TOKEN_KEY), "Login should have populated the ApiContext access token.");

        CreateEventRequest eventRequest = new CreateEventRequest(
                "ApiContext Chaining Event " + RandomDataUtils.uniqueId(), "Conference", "Test Venue", "Testville",
                DateUtils.futureIsoDate(30), 100.0, 50);
        ApiResponse createEventResponse = eventService.createEvent(eventRequest);
        createEventResponse.assertStatusCode(201);

        int eventId = createEventResponse.jsonPath().getInt("data.id");
        createdEventId = eventId;
        ApiContext.set("eventId", String.valueOf(eventId));

        CreateBookingRequest bookingRequest = new CreateBookingRequest(
                Integer.parseInt(ApiContext.get("eventId")), "Context Tester", RandomDataUtils.uniqueEmail("context.tester"),
                "+91-9876500001", 1);
        ApiResponse createBookingResponse = bookingService.createBooking(bookingRequest);
        createBookingResponse.assertStatusCode(201);

        BookingResponse booking = createBookingResponse.extract("data", BookingResponse.class);
        createdBookingId = booking.id();
        assertEquals(String.valueOf(booking.eventId()), ApiContext.get("eventId"),
                "Booking should reference the event ID chained through ApiContext.");
    }

    /**
     * Three sequential bookings against one event, interleaved with a cancellation - proving the
     * seat count accumulates/releases correctly across a chain of calls, not just a single
     * booking-then-cancel pair.
     */
    @Test(groups = "api")
    public void seatCountStaysCorrectAcrossMultipleSequentialBookingsAndACancellation() {
        authService.login(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));

        CreateEventRequest eventRequest = new CreateEventRequest(
                "Multi Booking Event " + RandomDataUtils.uniqueId(), "Conference", "Venue", "City", DateUtils.futureIsoDate(30), 20.0, 10);
        int eventId = eventService.createEvent(eventRequest).jsonPath().getInt("data.id");
        createdEventId = eventId;

        int firstBookingId = bookingService.createBooking(
                new CreateBookingRequest(eventId, "Buyer One", RandomDataUtils.uniqueEmail("buyer.one"), "+91-9876500001", 3))
                .jsonPath().getInt("data.id");
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), 7);

        int secondBookingId = bookingService.createBooking(
                new CreateBookingRequest(eventId, "Buyer Two", RandomDataUtils.uniqueEmail("buyer.two"), "+91-9876500002", 2))
                .jsonPath().getInt("data.id");
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), 5);

        bookingService.cancelBooking(firstBookingId).assertStatusCode(200);
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), 8, "Cancelling the 3-seat booking should restore just those 3.");

        createdBookingId = secondBookingId; // the only one still standing - cleanup() cancels it.
    }

    /**
     * Deleting an event cascades to its bookings (per the API's own documented behavior) - a
     * booking made through it becomes unreachable afterward rather than orphaned.
     */
    @Test(groups = "api")
    public void deletingAnEventCascadesToItsBookings() {
        authService.login(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));

        CreateEventRequest eventRequest = new CreateEventRequest(
                "Cascade Delete Event " + RandomDataUtils.uniqueId(), "Conference", "Venue", "City", DateUtils.futureIsoDate(30), 20.0, 5);
        int eventId = eventService.createEvent(eventRequest).jsonPath().getInt("data.id");
        createdEventId = eventId;

        int bookingId = bookingService.createBooking(
                new CreateBookingRequest(eventId, "Cascade Buyer", RandomDataUtils.uniqueEmail("cascade.buyer"), "+91-9876500003", 1))
                .jsonPath().getInt("data.id");

        eventService.deleteEvent(eventId).assertStatusCode(200);
        createdEventId = null; // already gone.

        bookingService.getBooking(bookingId).assertStatusCode(404);
        // Nothing left for cleanup() to cancel - the cascade already removed it.
    }
}
