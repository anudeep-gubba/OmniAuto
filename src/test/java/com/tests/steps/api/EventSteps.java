package com.tests.steps.api;

import com.framework.api.ApiClient;
import com.framework.api.ApiRequest;
import com.framework.api.ApiResponse;
import com.framework.utils.DateUtils;
import com.framework.utils.RandomDataUtils;
import com.tests.application.requests.CreateEventRequest;
import com.tests.application.responses.EventResponse;
import com.tests.application.testdata.TestDataSurface;
import com.tests.application.testdata.api.AuthApiTestCase;
import com.tests.application.testdata.api.AuthApiTestCase.AuthApiData;
import com.tests.application.testdata.api.EventPayloadTestCase;
import com.tests.application.testdata.api.EventPayloadTestCase.EventPayloadData;
import com.tests.steps.shared.ApiScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertFalse;
import static com.framework.utils.Verify.assertNotNull;
import static com.framework.utils.Verify.assertTrue;

/**
 * Steps behind {@code features/api/events.feature} - a mechanical lift of the old
 * {@code com.tests.tests.api.EventApiTest} {@code @Test} method bodies into Given/When/Then
 * steps; every service call and assertion is unchanged.
 */
public class EventSteps {

    private final ApiScenarioContext context;

    private ApiResponse response;
    private CreateEventRequest lastRequest;
    private int lastEventId;

    public EventSteps(ApiScenarioContext context) {
        this.context = context;
    }

    private static EventPayloadData data(String caseName) {
        return TestDataSurface.API.getCaseData(caseName, EventPayloadTestCase.class);
    }

    private static CreateEventRequest toRequest(String title, EventPayloadData eventData) {
        String eventDate = eventData.eventDate() != null ? eventData.eventDate() : DateUtils.futureIsoDate(eventData.daysInFuture());
        return new CreateEventRequest(title, eventData.eventDescription(), eventData.category(), eventData.venue(), eventData.city(),
                eventDate, eventData.price(), eventData.totalSeats(), eventData.imageUrl());
    }

    // ---------------------------------------------------------------- create

    @When("I create an event titled {string} from the {string} event test data")
    public void iCreateAnEventTitled(String title, String caseName) {
        lastRequest = toRequest(title + " " + RandomDataUtils.uniqueId(), data(caseName));
        response = context.eventService.createEvent(lastRequest);
        if (response.statusCode() == 201) {
            lastEventId = response.jsonPath().getInt("data.id");
            context.createdEventId = lastEventId;
        }
    }

    @Given("I have created an event titled {string} from the {string} event test data")
    public void iHaveCreatedAnEventTitled(String title, String caseName) {
        iCreateAnEventTitled(title, caseName);
    }

    @Given("I have created an event titled {string} from the {string} event test data, untracked")
    public void iHaveCreatedAnUntrackedEventTitled(String title, String caseName) {
        lastRequest = toRequest(title + " " + RandomDataUtils.uniqueId(), data(caseName));
        response = context.eventService.createEvent(lastRequest);
        lastEventId = response.jsonPath().getInt("data.id");
        // Deliberately not set on context.createdEventId - the scenario deletes it itself, so
        // ApiHooks would find nothing left to clean up, matching the original test's own comment.
    }

    @When("I create an event with an empty request body")
    public void iCreateAnEventWithAnEmptyRequestBody() {
        response = ApiClient.execute(ApiRequest.post("/events").body(Map.of()));
    }

    @Then("every submitted field should be persisted on the created event")
    public void everySubmittedFieldShouldBePersisted() {
        EventResponse event = response.extract("data", EventResponse.class);
        assertEquals(event.title(), lastRequest.title(), "Persisted event title should match what was submitted.");
        assertEquals(event.description(), lastRequest.description(), "Persisted event description should match what was submitted.");
        assertEquals(event.category(), lastRequest.category(), "Persisted event category should match what was submitted.");
        assertEquals(event.venue(), lastRequest.venue(), "Persisted event venue should match what was submitted.");
        assertEquals(event.city(), lastRequest.city(), "Persisted event city should match what was submitted.");
        assertEquals(event.totalSeats(), lastRequest.totalSeats(), "Persisted event total seats should match what was submitted.");
        // availableSeats is automatically set equal to totalSeats on creation, per the API's own documented behavior.
        assertEquals(event.availableSeats(), lastRequest.totalSeats(), "A freshly created event should start with every seat available.");
        assertEquals(event.imageUrl(), lastRequest.imageUrl(), "Persisted event image URL should match what was submitted.");
    }

    @Then("the created event should be assigned a positive numeric id")
    public void theCreatedEventShouldBeAssignedAPositiveNumericId() {
        assertTrue(lastEventId > 0, "Created event should be assigned a positive numeric id.");
    }

    @Then("every required event field should be flagged as missing")
    public void everyRequiredEventFieldShouldBeFlaggedAsMissing() {
        List<String> fields = response.jsonPath().getList("details.field", String.class);
        assertTrue(fields.containsAll(List.of("title", "category", "venue", "city", "eventDate", "price", "totalSeats")),
                "Every required field should be flagged: " + fields);
    }

    // ---------------------------------------------------------------- get

    @When("I get the created event by id")
    public void iGetTheCreatedEventById() {
        response = context.eventService.getEvent(lastEventId);
    }

    @When("I get event id {int}")
    public void iGetEventId(int eventId) {
        response = context.eventService.getEvent(eventId);
    }

    @When("I get the event with id {string}")
    public void iGetTheEventWithId(String rawId) {
        response = ApiClient.execute(ApiRequest.get("/events/{id}").pathParam("id", rawId));
    }

    @Then("the get-event response should be 200 with the same id")
    public void theGetEventResponseShouldBe200WithTheSameId() {
        response.assertStatusCode(200);
        assertEquals(response.jsonPath().getInt("data.id"), lastEventId);
    }

    @And("I log out and register a brand-new random second account using the {string} auth test data")
    public void iLogOutAndRegisterASecondAccount(String caseName) {
        AuthApiData secondAccount = TestDataSurface.API.getCaseData(caseName, AuthApiTestCase.class);
        context.authService.logout();
        context.authService.register(RandomDataUtils.uniqueEmail("event.isolation"), secondAccount.password());
    }

    // ---------------------------------------------------------------- list

    @When("I list events on page {int} with a limit of {int}")
    public void iListEventsOnPageWithALimitOf(int page, int limit) {
        response = context.eventService.listEvents(page, limit);
    }

    @Then("the list-events response should be a paginated envelope of at most {int} results")
    public void theListEventsResponseShouldBeAPaginatedEnvelope(int limit) {
        response.assertStatusCode(200);
        assertTrue(response.jsonPath().getBoolean("success"), "List response should report success.");
        assertEquals(response.jsonPath().getInt("pagination.page"), 1, "Response pagination should echo back the requested page.");
        assertEquals(response.jsonPath().getInt("pagination.limit"), limit, "Response pagination should echo back the requested limit.");
        assertTrue(response.jsonPath().getList("data").size() <= limit, "Result count should not exceed the requested page limit.");
    }

    @When("I list events filtered by the {string} event test data's category")
    public void iListEventsFilteredByCategory(String caseName) {
        EventPayloadData eventData = data(caseName);
        response = context.eventService.listEvents(Map.of("category", eventData.category(), "limit", 100));
    }

    @Then("every listed event should be in the {string} event test data's category")
    public void everyListedEventShouldBeInCategory(String caseName) {
        EventPayloadData eventData = data(caseName);
        response.assertStatusCode(eventData.expectedStatusCode());
        List<String> categories = response.jsonPath().getList("data.category", String.class);
        assertFalse(categories.isEmpty(), "Filtering by a category with a just-created event should return at least one result.");
        assertTrue(categories.stream().allMatch(eventData.category()::equals), "Every result should be " + eventData.category() + ": " + categories);
    }

    @When("I search events for {string}")
    public void iSearchEventsFor(String query) {
        response = context.eventService.listEvents(Map.of("search", query));
    }

    @Then("the search results should include the created event's title")
    public void theSearchResultsShouldIncludeTheCreatedEventsTitle() {
        response.assertStatusCode(200);
        List<String> titles = response.jsonPath().getList("data.title", String.class);
        assertTrue(titles.contains(lastRequest.title()), "Search results should include the newly created event: " + titles);
    }

    @When("I list all events")
    public void iListAllEvents() {
        response = context.eventService.listEvents();
    }

    // ---------------------------------------------------------------- update

    @When("I update the created event to {string} using the {string} event test data")
    public void iUpdateTheCreatedEventTo(String newTitle, String caseName) {
        EventPayloadData updateData = data(caseName);
        lastRequest = toRequest(newTitle + " " + RandomDataUtils.uniqueId(), updateData);
        response = context.eventService.updateEvent(lastEventId, lastRequest);
    }

    @When("I update event id {int} to {string} using the {string} event test data")
    public void iUpdateEventIdTo(int eventId, String newTitle, String caseName) {
        response = context.eventService.updateEvent(eventId, toRequest(newTitle, data(caseName)));
    }

    @Then("the update-event response should match the {string} event test data's expected status code")
    public void theUpdateEventResponseShouldMatchExpectedStatusCode(String caseName) {
        response.assertStatusCode(data(caseName).expectedStatusCode());
    }

    @Then("the update response and a fresh GET should both show the new fields")
    public void theUpdateResponseAndAFreshGetShouldBothShowTheNewFields() {
        assertEquals(response.jsonPath().getString("data.title"), lastRequest.title(), "Update response should echo back the new title.");
        assertEquals(response.jsonPath().getString("data.category"), lastRequest.category(), "Update response should echo back the new category.");
        assertEquals(response.jsonPath().getInt("data.totalSeats"), lastRequest.totalSeats(), "Update response should echo back the new seat count.");

        EventResponse fetched = context.eventService.getEvent(lastEventId).extract("data", EventResponse.class);
        assertEquals(fetched.title(), lastRequest.title(), "A fresh GET after updating should show the new title durably, not just in the PUT's own response.");
    }

    // ---------------------------------------------------------------- delete

    @When("I delete the created event")
    public void iDeleteTheCreatedEvent() {
        response = context.eventService.deleteEvent(lastEventId);
    }

    @When("I delete the created event again")
    public void iDeleteTheCreatedEventAgain() {
        response = context.eventService.deleteEvent(lastEventId);
    }

    @When("I delete event id {int}")
    public void iDeleteEventId(int eventId) {
        response = context.eventService.deleteEvent(eventId);
    }

    @Then("getting the deleted event should return 404")
    public void gettingTheDeletedEventShouldReturn404() {
        context.eventService.getEvent(lastEventId).assertStatusCode(404);
    }

    // ---------------------------------------------------------------- shared response assertions

    @Then("the event response status code should be {int}")
    public void theEventResponseStatusCodeShouldBe(int statusCode) {
        response.assertStatusCode(statusCode);
    }

    @Then("the event response should include an explanatory error message")
    public void theEventResponseShouldIncludeAnExplanatoryErrorMessage() {
        assertNotNull(response.jsonPath().getString("error"), "Deleting a non-existent event should return an explanatory error message.");
    }

    @Then("the create-event response should match the {string} event test data's expected status code")
    public void theCreateEventResponseShouldMatchExpectedStatusCode(String caseName) {
        response.assertStatusCode(data(caseName).expectedStatusCode());
    }

    @Then("the create-event response should match the {string} event test data's expected status and error")
    public void theCreateEventResponseShouldMatchExpectedStatusAndError(String caseName) {
        EventPayloadData eventData = data(caseName);
        response.assertStatusCode(eventData.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), eventData.expectedError());
    }

    @Then("the event response should match the {string} event test data's expected status and error")
    public void theEventResponseShouldMatchExpectedStatusAndError(String caseName) {
        EventPayloadData eventData = data(caseName);
        response.assertStatusCode(eventData.expectedStatusCode());
        assertEquals(response.jsonPath().getString("error"), eventData.expectedError());
    }

    @Then("the event's validation errors should flag both {string} and {string}")
    public void theEventsValidationErrorsShouldFlagBoth(String firstField, String secondField) {
        List<String> fields = response.jsonPath().getList("details.field", String.class);
        assertTrue(fields.contains(firstField), "Validation errors should flag the negative " + firstField + ".");
        assertTrue(fields.contains(secondField), "Validation errors should flag the negative " + secondField + ".");
    }

    @Then("the first validation error should match the {string} event test data's expected field and message")
    public void theFirstValidationErrorShouldMatch(String caseName) {
        EventPayloadData eventData = data(caseName);
        assertEquals(response.jsonPath().getString("details[0].field"), eventData.expectedField());
        assertEquals(response.jsonPath().getString("details[0].message"), eventData.expectedMessage());
    }
}
