package com.tests.tests.api;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.tests.application.requests.CreateEventRequest;
import com.tests.application.responses.EventResponse;
import com.tests.application.services.EventService;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.AuthApiTestCase.AuthApiData;
import com.tests.application.testdata.api.AuthApiTestCase;
import com.tests.application.testdata.api.EventPayloadTestCase.EventPayloadData;
import com.tests.application.testdata.api.EventPayloadTestCase;
import com.framework.utils.DateUtils;
import com.framework.utils.RandomDataUtils;
import com.tests.application.base.BaseApiTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Full positive/negative coverage of eventhub's {@code /events} CRUD endpoints, against the live
 * API. Every event this class creates is tracked and deleted in {@link #tearDownTestData()}, so
 * a failed assertion mid-test still leaves the seeded account clean.
 *
 * <p>Test data (metadata/data per case, for easy identification in a failure or a report)
 * lives in {@code testdata/json/api/api.json}, separate from {@code testdata/json/web/web.json}
 * (Web) and {@code testdata/json/android/android.json}/{@code testdata/json/ios/ios.json} (Mobile) -
 * "maintain separate files per surface" per the task this suite was written for.</p>
 */
public class EventApiTest extends BaseApiTest {

    private final EventService eventService = new EventService();

    private Integer createdEventId;

    // alwaysRun = true: TestNG silently skips a @BeforeMethod lacking this when a group
    // include-filter is active (-Dgroups=smoke), leaving the @Test unauthenticated instead of
    // simply not running - an easy-to-miss trap, so every setup/teardown method here opts in.
    @BeforeMethod(alwaysRun = true)
    public void logIn() {
        loginWithSeededAccount();
    }

    @Override
    protected void tearDownTestData() {
        if (createdEventId != null) {
            eventService.deleteEvent(createdEventId);
            createdEventId = null;
        }
    }

    private static CreateEventRequest toRequest(String title, EventPayloadData data) {
        String eventDate = data.eventDate() != null ? data.eventDate() : DateUtils.futureIsoDate(data.daysInFuture());
        return new CreateEventRequest(title, data.eventDescription(), data.category(), data.venue(), data.city(),
                eventDate, data.price(), data.totalSeats(), data.imageUrl());
    }

    private CreateEventRequest freshEventRequest(String title) {
        return toRequest(title, TestDataSurface.API.getCaseData("defaultEvent", EventPayloadTestCase.class));
    }

    // ---------------------------------------------------------------- create: positive

    @Test(groups = {"smoke", "api"})
    public void creatingAnEventWithAllFieldsPersistsEveryField() {
        EventPayloadData data = TestDataSurface.API.getCaseData("fullFieldsEvent", EventPayloadTestCase.class);
        CreateEventRequest request = toRequest("Full Fields Event " + RandomDataUtils.uniqueId(), data);

        ApiResponse response = eventService.createEvent(request);
        response.assertStatusCode(201);

        createdEventId = response.jsonPath().getInt("data.id");
        EventResponse event = response.extract("data", EventResponse.class);
        assertEquals(event.title(), request.title());
        assertEquals(event.description(), request.description());
        assertEquals(event.category(), request.category());
        assertEquals(event.venue(), request.venue());
        assertEquals(event.city(), request.city());
        assertEquals(event.totalSeats(), request.totalSeats());
        // availableSeats is automatically set equal to totalSeats on creation, per the API's own documented behavior.
        assertEquals(event.availableSeats(), request.totalSeats());
        assertEquals(event.imageUrl(), request.imageUrl());
    }

    @Test(groups = "api")
    public void creatingAnEventWithOnlyRequiredFieldsSucceeds() {
        CreateEventRequest request = freshEventRequest("Required Fields Only Event " + RandomDataUtils.uniqueId());

        ApiResponse response = eventService.createEvent(request);
        response.assertStatusCode(201);

        createdEventId = response.jsonPath().getInt("data.id");
        assertTrue(createdEventId > 0);
    }

    // ---------------------------------------------------------------- create: negative

    @Test(groups = "api")
    public void creatingAnEventWithoutAuthReturns401() {
        authService.logout();

        ApiResponse response = eventService.createEvent(freshEventRequest("Should Never Be Created"));

        response.assertStatusCode(401);
        assertEquals(response.jsonPath().getString("error"), "Unauthorized");
    }

    @Test(groups = "api")
    public void creatingAnEventWithNoBodyFieldsReturnsEveryRequiredFieldAsAValidationError() {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/events").body(Map.of()));

        response.assertStatusCode(400);
        List<String> fields = response.jsonPath().getList("details.field", String.class);
        assertTrue(fields.containsAll(List.of("title", "category", "venue", "city", "eventDate", "price", "totalSeats")),
                "Every required field should be flagged: " + fields);
    }

    @Test(groups = "api")
    public void creatingAnEventWithNegativePriceAndSeatsFailsValidation() {
        EventPayloadData data = TestDataSurface.API.getCaseData("negativePriceAndSeatsEvent", EventPayloadTestCase.class);
        CreateEventRequest request = toRequest("Negative Values Event", data);

        ApiResponse response = eventService.createEvent(request);

        response.assertStatusCode(400);
        List<String> fields = response.jsonPath().getList("details.field", String.class);
        assertTrue(fields.contains("price"));
        assertTrue(fields.contains("totalSeats"));
    }

    @Test(groups = "api")
    public void creatingAnEventWithAPastDateFailsValidation() {
        EventPayloadData data = TestDataSurface.API.getCaseData("pastDateEvent", EventPayloadTestCase.class);
        CreateEventRequest request = toRequest("Past Date Event", data);

        ApiResponse response = eventService.createEvent(request);

        response.assertStatusCode(400);
        assertEquals(response.jsonPath().getString("details[0].field"), "eventDate");
        assertEquals(response.jsonPath().getString("details[0].message"), "Event date must be in the future");
    }

    // ---------------------------------------------------------------- get by id

    @Test(groups = {"smoke", "api"})
    public void gettingAnExistingEventByIdReturnsIt() {
        createdEventId = eventService.createEvent(freshEventRequest("Get By Id Event " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id");

        ApiResponse response = eventService.getEvent(createdEventId);

        response.assertStatusCode(200);
        assertEquals(response.jsonPath().getInt("data.id"), createdEventId.intValue());
    }

    @Test(groups = "api")
    public void gettingANonexistentEventReturns404WithAnExplanatoryMessage() {
        ApiResponse response = eventService.getEvent(999_999);

        response.assertStatusCode(404);
        assertEquals(response.jsonPath().getString("error"), "Event with id 999999 not found");
    }

    /**
     * Documented, live-verified quirk: a non-numeric path segment is not caught by input
     * validation before it reaches the database layer, and surfaces as a generic 500 rather than
     * a 400/404 - worth locking in as a regression test precisely because it is surprising.
     */
    @Test(groups = "api")
    public void gettingAnEventByANonNumericIdReturns500NotAValidationError() {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/events/{id}").pathParam("id", "not-a-number"));

        response.assertStatusCode(500);
    }

    @Test(groups = "api")
    public void gettingAnotherUsersEventReturns404NotForbidden() {
        // Verified live: eventhub scopes every event to its creating account. A second account
        // gets an ordinary "not found" 404 for a real event ID it simply doesn't own - not 403.
        AuthApiData secondAccount = TestDataSurface.API.getCaseData("secondAccountForEventIsolation", AuthApiTestCase.class);
        String secondUserEmail = RandomDataUtils.uniqueEmail("event.isolation");
        createdEventId = eventService.createEvent(freshEventRequest("Isolation Target Event " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id");
        authService.logout();

        authService.register(secondUserEmail, secondAccount.password());
        try {
            ApiResponse response = eventService.getEvent(createdEventId);
            response.assertStatusCode(404);
        } finally {
            authService.logout();
            loginWithSeededAccount();
        }
    }

    // ---------------------------------------------------------------- list: positive

    @Test(groups = {"smoke", "api"})
    public void listingEventsReturnsAPaginatedEnvelope() {
        ApiResponse response = eventService.listEvents(1, 5);

        response.assertStatusCode(200);
        assertTrue(response.jsonPath().getBoolean("success"));
        assertEquals(response.jsonPath().getInt("pagination.page"), 1);
        assertEquals(response.jsonPath().getInt("pagination.limit"), 5);
        assertTrue(response.jsonPath().getList("data").size() <= 5);
    }

    @Test(groups = "api")
    public void listingEventsFiltersByCategory() {
        EventPayloadData data = TestDataSurface.API.getCaseData("sportsCategoryEvent", EventPayloadTestCase.class);
        createdEventId = eventService.createEvent(
                toRequest("Category Filter Event " + RandomDataUtils.uniqueId(), data))
                .jsonPath().getInt("data.id");

        ApiResponse response = eventService.listEvents(Map.of("category", data.category(), "limit", 100));

        response.assertStatusCode(200);
        List<String> categories = response.jsonPath().getList("data.category", String.class);
        assertFalse(categories.isEmpty());
        assertTrue(categories.stream().allMatch(data.category()::equals), "Every result should be " + data.category() + ": " + categories);
    }

    @Test(groups = "api")
    public void listingEventsFreeTextSearchMatchesTitle() {
        String uniqueTitle = "Searchable Unique Title " + RandomDataUtils.uniqueId();
        createdEventId = eventService.createEvent(freshEventRequest(uniqueTitle)).jsonPath().getInt("data.id");

        ApiResponse response = eventService.listEvents(Map.of("search", "Searchable Unique Title"));

        response.assertStatusCode(200);
        List<String> titles = response.jsonPath().getList("data.title", String.class);
        assertTrue(titles.contains(uniqueTitle), "Search results should include the newly created event: " + titles);
    }

    @Test(groups = "api")
    public void listingEventsWithoutAuthReturns401() {
        authService.logout();

        ApiResponse response = eventService.listEvents();

        response.assertStatusCode(401);
    }

    // ---------------------------------------------------------------- update

    @Test(groups = {"smoke", "api"})
    public void updatingAnEventChangesItsFields() {
        createdEventId = eventService.createEvent(freshEventRequest("Before Update " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id");

        EventPayloadData updateData = TestDataSurface.API.getCaseData("eventUpdate", EventPayloadTestCase.class);
        CreateEventRequest updateRequest = toRequest("After Update " + RandomDataUtils.uniqueId(), updateData);
        ApiResponse response = eventService.updateEvent(createdEventId, updateRequest);

        response.assertStatusCode(200);
        assertEquals(response.jsonPath().getString("data.title"), updateRequest.title());
        assertEquals(response.jsonPath().getString("data.category"), "Festival");
        assertEquals(response.jsonPath().getInt("data.totalSeats"), 75);

        EventResponse fetched = eventService.getEvent(createdEventId).extract("data", EventResponse.class);
        assertEquals(fetched.title(), updateRequest.title());
    }

    @Test(groups = "api")
    public void updatingANonexistentEventReturns404() {
        ApiResponse response = eventService.updateEvent(999_999, freshEventRequest("Ghost Update"));

        response.assertStatusCode(404);
    }

    @Test(groups = "api")
    public void updatingAnEventWithInvalidDataFailsValidation() {
        createdEventId = eventService.createEvent(freshEventRequest("Invalid Update Target " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id");

        EventPayloadData badData = TestDataSurface.API.getCaseData("invalidEventUpdate", EventPayloadTestCase.class);
        CreateEventRequest badUpdate = toRequest("Bad Update", badData);
        ApiResponse response = eventService.updateEvent(createdEventId, badUpdate);

        response.assertStatusCode(400);
    }

    // ---------------------------------------------------------------- delete

    @Test(groups = {"smoke", "api"})
    public void deletingAnEventThenGettingItReturns404() {
        int eventId = eventService.createEvent(freshEventRequest("Delete Me " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id");

        ApiResponse deleteResponse = eventService.deleteEvent(eventId);
        deleteResponse.assertStatusCode(200);

        ApiResponse getResponse = eventService.getEvent(eventId);
        getResponse.assertStatusCode(404);
        // Not tracked in createdEventId - already deleted, so cleanup() has nothing left to do.
    }

    @Test(groups = "api")
    public void deletingAnAlreadyDeletedEventReturns404OnTheSecondCall() {
        int eventId = eventService.createEvent(freshEventRequest("Double Delete " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id");
        eventService.deleteEvent(eventId).assertStatusCode(200);

        ApiResponse secondDelete = eventService.deleteEvent(eventId);

        secondDelete.assertStatusCode(404);
    }

    @Test(groups = "api")
    public void deletingANonexistentEventReturns404() {
        ApiResponse response = eventService.deleteEvent(999_999);

        response.assertStatusCode(404);
        assertNotNull(response.jsonPath().getString("error"));
    }
}
