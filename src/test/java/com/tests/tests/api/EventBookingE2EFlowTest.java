package com.tests.tests.api;

import com.framework.api.ApiContext;
import com.framework.api.ApiResponse;
import com.tests.application.requests.CreateBookingRequest;
import com.tests.application.requests.CreateEventRequest;
import com.tests.application.responses.BookingResponse;
import com.tests.application.responses.EventResponse;
import com.tests.application.services.BookingService;
import com.tests.application.services.EventService;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.AuthApiTestCase.AuthApiData;
import com.tests.application.testdata.api.AuthApiTestCase;
import com.tests.application.testdata.api.BookingApiTestCase.BookingApiData;
import com.tests.application.testdata.api.BookingApiTestCase;
import com.tests.application.testdata.api.EventPayloadTestCase.EventPayloadData;
import com.tests.application.testdata.api.EventPayloadTestCase;
import com.framework.utils.DateUtils;
import com.framework.utils.RandomDataUtils;
import com.tests.application.base.BaseApiTest;
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
 *
 * <p>Test data (metadata/data per step, for easy identification in a failure or a report)
 * lives in {@code testdata/json/api/api.json}, one entry per journey step - see {@link
 * AuthApiTestCase}/{@link EventPayloadTestCase}/{@link BookingApiTestCase} for the shapes shared
 * with {@link AuthApiTest}/{@link EventApiTest}/{@link BookingApiTest}.</p>
 */
public class EventBookingE2EFlowTest extends BaseApiTest {

    private final EventService eventService = new EventService();
    private final BookingService bookingService = new BookingService();

    private Integer createdBookingId;
    private Integer createdEventId;

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
        ApiContext.clear();
    }

    private static CreateEventRequest toEventRequest(String title, EventPayloadData data) {
        String eventDate = data.eventDate() != null ? data.eventDate() : DateUtils.futureIsoDate(data.daysInFuture());
        return new CreateEventRequest(title, data.eventDescription(), data.category(), data.venue(), data.city(),
                eventDate, data.price(), data.totalSeats(), data.imageUrl());
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
        AuthApiData registration = TestDataSurface.API.getCaseData("e2eFullLifecycleRegistration", AuthApiTestCase.class);
        String email = RandomDataUtils.uniqueEmail("e2e.flow");
        authService.register(email, registration.password());

        // 2. Create an event as that account.
        EventPayloadData eventData = TestDataSurface.API.getCaseData("e2eFullLifecycleEvent", EventPayloadTestCase.class);
        String uniqueTitle = "E2E Flow Event " + RandomDataUtils.uniqueId();
        CreateEventRequest eventRequest = toEventRequest(uniqueTitle, eventData);
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
        EventPayloadData updateData = TestDataSurface.API.getCaseData("e2eFullLifecycleEventUpdate", EventPayloadTestCase.class);
        CreateEventRequest updateRequest = toEventRequest(uniqueTitle, updateData);
        eventService.updateEvent(eventId, updateRequest).assertStatusCode(200);
        assertEquals(eventService.getEvent(eventId).jsonPath().getString("data.venue"), updateData.venue());

        // 5. Book tickets, chaining the event ID extracted in step 2 straight into the request.
        BookingApiData bookingData = TestDataSurface.API.getCaseData("e2eFullLifecycleBooking", BookingApiTestCase.class);
        CreateBookingRequest bookingRequest = new CreateBookingRequest(
                eventId, bookingData.customerName(), RandomDataUtils.uniqueEmail("e2e.customer"), bookingData.customerPhone(), bookingData.quantity());
        ApiResponse createBookingResponse = bookingService.createBooking(bookingRequest);
        createBookingResponse.assertStatusCode(201);
        BookingResponse booking = createBookingResponse.extract("data", BookingResponse.class);
        createdBookingId = booking.id();
        assertEquals(booking.eventId(), eventId);
        assertEquals(booking.status(), "confirmed");

        // 6. Confirm the atomic seat decrement - the booking response's own nested "event" is a
        // stale pre-transaction snapshot (verified live), so a fresh GET is required.
        EventResponse afterBooking = eventService.getEvent(eventId).extract("data", EventResponse.class);
        int expectedAvailable = eventData.totalSeats() - bookingData.quantity();
        assertEquals(afterBooking.availableSeats(), expectedAvailable,
                eventData.totalSeats() + " total - " + bookingData.quantity() + " booked should leave " + expectedAvailable + " available.");

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
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), eventData.totalSeats(),
                "Cancelling should restore all " + bookingData.quantity() + " seats.");
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
        loginWithSeededAccount();
        assertTrue(ApiContext.has(ApiContext.ACCESS_TOKEN_KEY), "Login should have populated the ApiContext access token.");

        EventPayloadData eventData = TestDataSurface.API.getCaseData("e2eContextChainingEvent", EventPayloadTestCase.class);
        CreateEventRequest eventRequest = toEventRequest("ApiContext Chaining Event " + RandomDataUtils.uniqueId(), eventData);
        ApiResponse createEventResponse = eventService.createEvent(eventRequest);
        createEventResponse.assertStatusCode(201);

        int eventId = createEventResponse.jsonPath().getInt("data.id");
        createdEventId = eventId;
        ApiContext.set("eventId", String.valueOf(eventId));

        BookingApiData bookingData = TestDataSurface.API.getCaseData("e2eContextChainingBooking", BookingApiTestCase.class);
        CreateBookingRequest bookingRequest = new CreateBookingRequest(
                Integer.parseInt(ApiContext.get("eventId")), bookingData.customerName(), RandomDataUtils.uniqueEmail("context.tester"),
                bookingData.customerPhone(), bookingData.quantity());
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
        loginWithSeededAccount();

        EventPayloadData eventData = TestDataSurface.API.getCaseData("e2eSequentialBookingsEvent", EventPayloadTestCase.class);
        CreateEventRequest eventRequest = toEventRequest("Multi Booking Event " + RandomDataUtils.uniqueId(), eventData);
        int eventId = eventService.createEvent(eventRequest).jsonPath().getInt("data.id");
        createdEventId = eventId;

        BookingApiData firstBookingData = TestDataSurface.API.getCaseData("e2eSequentialBookingOne", BookingApiTestCase.class);
        int firstBookingId = bookingService.createBooking(
                new CreateBookingRequest(eventId, firstBookingData.customerName(), RandomDataUtils.uniqueEmail("buyer.one"),
                        firstBookingData.customerPhone(), firstBookingData.quantity()))
                .jsonPath().getInt("data.id");
        int afterFirstBooking = eventData.totalSeats() - firstBookingData.quantity();
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), afterFirstBooking);

        BookingApiData secondBookingData = TestDataSurface.API.getCaseData("e2eSequentialBookingTwo", BookingApiTestCase.class);
        int secondBookingId = bookingService.createBooking(
                new CreateBookingRequest(eventId, secondBookingData.customerName(), RandomDataUtils.uniqueEmail("buyer.two"),
                        secondBookingData.customerPhone(), secondBookingData.quantity()))
                .jsonPath().getInt("data.id");
        int afterSecondBooking = afterFirstBooking - secondBookingData.quantity();
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), afterSecondBooking);

        bookingService.cancelBooking(firstBookingId).assertStatusCode(200);
        assertEquals(eventService.getEvent(eventId).jsonPath().getInt("data.availableSeats"), afterSecondBooking + firstBookingData.quantity(),
                "Cancelling the " + firstBookingData.quantity() + "-seat booking should restore just those " + firstBookingData.quantity() + ".");

        createdBookingId = secondBookingId; // the only one still standing - cleanup() cancels it.
    }

    /**
     * Deleting an event cascades to its bookings (per the API's own documented behavior) - a
     * booking made through it becomes unreachable afterward rather than orphaned.
     */
    @Test(groups = "api")
    public void deletingAnEventCascadesToItsBookings() {
        loginWithSeededAccount();

        EventPayloadData eventData = TestDataSurface.API.getCaseData("e2eCascadeDeleteEvent", EventPayloadTestCase.class);
        CreateEventRequest eventRequest = toEventRequest("Cascade Delete Event " + RandomDataUtils.uniqueId(), eventData);
        int eventId = eventService.createEvent(eventRequest).jsonPath().getInt("data.id");
        createdEventId = eventId;

        BookingApiData bookingData = TestDataSurface.API.getCaseData("e2eCascadeDeleteBooking", BookingApiTestCase.class);
        int bookingId = bookingService.createBooking(
                new CreateBookingRequest(eventId, bookingData.customerName(), RandomDataUtils.uniqueEmail("cascade.buyer"), bookingData.customerPhone(), bookingData.quantity()))
                .jsonPath().getInt("data.id");

        eventService.deleteEvent(eventId).assertStatusCode(200);
        createdEventId = null; // already gone.

        bookingService.getBooking(bookingId).assertStatusCode(404);
        // Nothing left for cleanup() to cancel - the cascade already removed it.
    }
}
