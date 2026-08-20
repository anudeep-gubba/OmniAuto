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

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertFalse;
import static com.framework.utils.Verify.assertNotNull;
import static com.framework.utils.Verify.assertTrue;

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

    // ThreadLocal, not a plain field (audit finding, verified live with -Dparallel=methods
    // -DthreadCount=8): TestNG runs every @Test method of a class on one shared instance under
    // method-level parallelism, not one instance per thread/method - a plain field here let one
    // thread's write clobber another's before it read the value back, corrupting both the
    // assertion (wrong event ID compared) and cleanup (tearDownTestData() could delete a
    // different thread's still-in-use event). Same reasoning as com.framework.api.ApiContext/
    // ConfigManager's own thread-local tiers.
    private final ThreadLocal<Integer> createdEventId = new ThreadLocal<>();

    // alwaysRun = true: TestNG silently skips a @BeforeMethod lacking this when a group
    // include-filter is active (-Dgroups=smoke), leaving the @Test unauthenticated instead of
    // simply not running - an easy-to-miss trap, so every setup/teardown method here opts in.
    @BeforeMethod(alwaysRun = true)
    public void logIn() {
        loginWithSeededAccount();
    }

    @Override
    protected void tearDownTestData() {
        if (createdEventId.get() != null) {
            eventService.deleteEvent(createdEventId.get());
            createdEventId.remove();
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

    @Test(groups = {"smoke", "api", "events", "positive"})
    public void creatingAnEventWithAllFieldsPersistsEveryField() {
        EventPayloadData data = TestDataSurface.API.getCaseData("fullFieldsEvent", EventPayloadTestCase.class);
        CreateEventRequest request = toRequest("Full Fields Event " + RandomDataUtils.uniqueId(), data);

        ApiResponse response = eventService.createEvent(request);
        response.assertStatusCode(data.expectedStatusCode());

        createdEventId.set(response.jsonPath().getInt("data.id"));
        EventResponse event = response.extract("data", EventResponse.class);
        assertEquals(event.title(), request.title(), "Persisted event title should match what was submitted.");
        assertEquals(event.description(), request.description(), "Persisted event description should match what was submitted.");
        assertEquals(event.category(), request.category(), "Persisted event category should match what was submitted.");
        assertEquals(event.venue(), request.venue(), "Persisted event venue should match what was submitted.");
        assertEquals(event.city(), request.city(), "Persisted event city should match what was submitted.");
        assertEquals(event.totalSeats(), request.totalSeats(), "Persisted event total seats should match what was submitted.");
        // availableSeats is automatically set equal to totalSeats on creation, per the API's own documented behavior.
        assertEquals(event.availableSeats(), request.totalSeats(), "A freshly created event should start with every seat available.");
        assertEquals(event.imageUrl(), request.imageUrl(), "Persisted event image URL should match what was submitted.");
    }

    @Test(groups = {"api", "events", "positive"})
    public void creatingAnEventWithOnlyRequiredFieldsSucceeds() {
        EventPayloadData data = TestDataSurface.API.getCaseData("defaultEvent", EventPayloadTestCase.class);
        CreateEventRequest request = toRequest("Required Fields Only Event " + RandomDataUtils.uniqueId(), data);

        ApiResponse response = eventService.createEvent(request);
        response.assertStatusCode(data.expectedStatusCode());

        createdEventId.set(response.jsonPath().getInt("data.id"));
        assertTrue(createdEventId.get() > 0, "Created event should be assigned a positive numeric id.");
    }

    // ---------------------------------------------------------------- create: negative

    @Test(groups = {"api", "events", "negative"})
    public void creatingAnEventWithoutAuthReturns401() {
        EventPayloadData data = TestDataSurface.API.getCaseData("unauthenticatedEventCreate", EventPayloadTestCase.class);
        authService.logout();

        ApiResponse response = eventService.createEvent(freshEventRequest("Should Never Be Created"));

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), data.expectedError());
    }

    @Test(groups = {"api", "events", "negative"})
    public void creatingAnEventWithNoBodyFieldsReturnsEveryRequiredFieldAsAValidationError() {
        ApiResponse response = ApiClient.execute(ApiRequest.post("/events").body(Map.of()));

        response.assertStatusCode(400);
        List<String> fields = response.jsonPath().getList("details.field", String.class);
        assertTrue(fields.containsAll(List.of("title", "category", "venue", "city", "eventDate", "price", "totalSeats")),
                "Every required field should be flagged: " + fields);
    }

    @Test(groups = {"api", "events", "negative"})
    public void creatingAnEventWithNegativePriceAndSeatsFailsValidation() {
        EventPayloadData data = TestDataSurface.API.getCaseData("negativePriceAndSeatsEvent", EventPayloadTestCase.class);
        CreateEventRequest request = toRequest("Negative Values Event", data);

        ApiResponse response = eventService.createEvent(request);

        response.assertStatusCode(data.expectedStatusCode());
        List<String> fields = response.jsonPath().getList("details.field", String.class);
        assertTrue(fields.contains("price"), "Validation errors should flag the negative price.");
        assertTrue(fields.contains("totalSeats"), "Validation errors should flag the negative seat count.");
    }

    @Test(groups = {"api", "events", "negative"})
    public void creatingAnEventWithAPastDateFailsValidation() {
        EventPayloadData data = TestDataSurface.API.getCaseData("pastDateEvent", EventPayloadTestCase.class);
        CreateEventRequest request = toRequest("Past Date Event", data);

        ApiResponse response = eventService.createEvent(request);

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("details[0].field"), data.expectedField());
        assertEquals(response.jsonPath().getString("details[0].message"), data.expectedMessage());
    }

    // ---------------------------------------------------------------- get by id

    @Test(groups = {"smoke", "api", "events", "positive"})
    public void gettingAnExistingEventByIdReturnsIt() {
        createdEventId.set(eventService.createEvent(freshEventRequest("Get By Id Event " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id"));

        ApiResponse response = eventService.getEvent(createdEventId.get());

        response.assertStatusCode(200);
        assertEquals(response.jsonPath().getInt("data.id"), createdEventId.get());
    }

    @Test(groups = {"api", "events", "negative"})
    public void gettingANonexistentEventReturns404WithAnExplanatoryMessage() {
        EventPayloadData data = TestDataSurface.API.getCaseData("nonexistentEventLookup", EventPayloadTestCase.class);
        ApiResponse response = eventService.getEvent(999_999);

        response.assertStatusCode(data.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), data.expectedError());
    }

    /**
     * Documented, live-verified quirk: a non-numeric path segment is not caught by input
     * validation before it reaches the database layer, and surfaces as a generic 500 rather than
     * a 400/404 - worth locking in as a regression test precisely because it is surprising.
     */
    @Test(groups = {"api", "events", "negative"})
    public void gettingAnEventByANonNumericIdReturns500NotAValidationError() {
        ApiResponse response = ApiClient.execute(ApiRequest.get("/events/{id}").pathParam("id", "not-a-number"));

        response.assertStatusCode(500);
    }

    @Test(groups = {"api", "events", "negative"})
    public void gettingAnotherUsersEventReturns404NotForbidden() {
        // Verified live: eventhub scopes every event to its creating account. A second account
        // gets an ordinary "not found" 404 for a real event ID it simply doesn't own - not 403.
        AuthApiData secondAccount = TestDataSurface.API.getCaseData("secondAccountForEventIsolation", AuthApiTestCase.class);
        String secondUserEmail = RandomDataUtils.uniqueEmail("event.isolation");
        createdEventId.set(eventService.createEvent(freshEventRequest("Isolation Target Event " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id"));
        authService.logout();

        authService.register(secondUserEmail, secondAccount.password());
        try {
            ApiResponse response = eventService.getEvent(createdEventId.get());
            response.assertStatusCode(404);
        } finally {
            authService.logout();
            loginWithSeededAccount();
        }
    }

    // ---------------------------------------------------------------- list: positive

    @Test(groups = {"smoke", "api", "events", "positive"})
    public void listingEventsReturnsAPaginatedEnvelope() {
        ApiResponse response = eventService.listEvents(1, 5);

        response.assertStatusCode(200);
        assertTrue(response.jsonPath().getBoolean("success"), "List response should report success.");
        assertEquals(response.jsonPath().getInt("pagination.page"), 1, "Response pagination should echo back the requested page.");
        assertEquals(response.jsonPath().getInt("pagination.limit"), 5, "Response pagination should echo back the requested limit.");
        assertTrue(response.jsonPath().getList("data").size() <= 5, "Result count should not exceed the requested page limit.");
    }

    @Test(groups = {"api", "events", "positive"})
    public void listingEventsFiltersByCategory() {
        EventPayloadData data = TestDataSurface.API.getCaseData("sportsCategoryEvent", EventPayloadTestCase.class);
        createdEventId.set(eventService.createEvent(
                toRequest("Category Filter Event " + RandomDataUtils.uniqueId(), data))
                .jsonPath().getInt("data.id"));

        ApiResponse response = eventService.listEvents(Map.of("category", data.category(), "limit", 100));

        response.assertStatusCode(data.expectedStatusCode());
        List<String> categories = response.jsonPath().getList("data.category", String.class);
        assertFalse(categories.isEmpty(), "Filtering by a category with a just-created event should return at least one result.");
        assertTrue(categories.stream().allMatch(data.category()::equals), "Every result should be " + data.category() + ": " + categories);
    }

    @Test(groups = {"api", "events", "positive"})
    public void listingEventsFreeTextSearchMatchesTitle() {
        String uniqueTitle = "Searchable Unique Title " + RandomDataUtils.uniqueId();
        createdEventId.set(eventService.createEvent(freshEventRequest(uniqueTitle)).jsonPath().getInt("data.id"));

        ApiResponse response = eventService.listEvents(Map.of("search", "Searchable Unique Title"));

        response.assertStatusCode(200);
        List<String> titles = response.jsonPath().getList("data.title", String.class);
        assertTrue(titles.contains(uniqueTitle), "Search results should include the newly created event: " + titles);
    }

    @Test(groups = {"api", "events", "negative"})
    public void listingEventsWithoutAuthReturns401() {
        authService.logout();

        ApiResponse response = eventService.listEvents();

        response.assertStatusCode(401);
    }

    // ---------------------------------------------------------------- update

    @Test(groups = {"smoke", "api", "events", "positive"})
    public void updatingAnEventChangesItsFields() {
        createdEventId.set(eventService.createEvent(freshEventRequest("Before Update " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id"));

        EventPayloadData updateData = TestDataSurface.API.getCaseData("eventUpdate", EventPayloadTestCase.class);
        CreateEventRequest updateRequest = toRequest("After Update " + RandomDataUtils.uniqueId(), updateData);
        ApiResponse response = eventService.updateEvent(createdEventId.get(), updateRequest);

        response.assertStatusCode(updateData.expectedStatusCode());
        assertEquals(response.jsonPath().getString("data.title"), updateRequest.title(), "Update response should echo back the new title.");
        assertEquals(response.jsonPath().getString("data.category"), updateRequest.category(), "Update response should echo back the new category.");
        assertEquals(response.jsonPath().getInt("data.totalSeats"), updateRequest.totalSeats(), "Update response should echo back the new seat count.");

        EventResponse fetched = eventService.getEvent(createdEventId.get()).extract("data", EventResponse.class);
        assertEquals(fetched.title(), updateRequest.title(), "A fresh GET after updating should show the new title durably, not just in the PUT's own response.");
    }

    @Test(groups = {"api", "events", "negative"})
    public void updatingANonexistentEventReturns404() {
        ApiResponse response = eventService.updateEvent(999_999, freshEventRequest("Ghost Update"));

        response.assertStatusCode(404);
    }

    @Test(groups = {"api", "events", "negative"})
    public void updatingAnEventWithInvalidDataFailsValidation() {
        createdEventId.set(eventService.createEvent(freshEventRequest("Invalid Update Target " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id"));

        EventPayloadData badData = TestDataSurface.API.getCaseData("invalidEventUpdate", EventPayloadTestCase.class);
        CreateEventRequest badUpdate = toRequest("Bad Update", badData);
        ApiResponse response = eventService.updateEvent(createdEventId.get(), badUpdate);

        response.assertStatusCode(badData.expectedStatusCode());
    }

    // ---------------------------------------------------------------- delete

    @Test(groups = {"smoke", "api", "events", "positive"})
    public void deletingAnEventThenGettingItReturns404() {
        int eventId = eventService.createEvent(freshEventRequest("Delete Me " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id");

        ApiResponse deleteResponse = eventService.deleteEvent(eventId);
        deleteResponse.assertStatusCode(200);

        ApiResponse getResponse = eventService.getEvent(eventId);
        getResponse.assertStatusCode(404);
        // Not tracked in createdEventId - already deleted, so cleanup() has nothing left to do.
    }

    @Test(groups = {"api", "events", "negative"})
    public void deletingAnAlreadyDeletedEventReturns404OnTheSecondCall() {
        int eventId = eventService.createEvent(freshEventRequest("Double Delete " + RandomDataUtils.uniqueId()))
                .jsonPath().getInt("data.id");
        eventService.deleteEvent(eventId).assertStatusCode(200);

        ApiResponse secondDelete = eventService.deleteEvent(eventId);

        secondDelete.assertStatusCode(404);
    }

    @Test(groups = {"api", "events", "negative"})
    public void deletingANonexistentEventReturns404() {
        ApiResponse response = eventService.deleteEvent(999_999);

        response.assertStatusCode(404);
        assertNotNull(response.jsonPath().getString("error"), "Deleting a non-existent event should return an explanatory error message.");
    }
}
