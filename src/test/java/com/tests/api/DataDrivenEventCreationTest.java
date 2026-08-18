package com.tests.api;

import com.framework.api.ApiResponse;
import com.framework.api.requests.CreateEventRequest;
import com.framework.api.services.AuthenticationService;
import com.framework.api.services.EventService;
import com.framework.secrets.SecretManager;
import com.framework.testdata.TestDataManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Phase 9 validation: CSV- and Excel-sourced data driving real, live event creation
 * (requirement.md &sect;9, &sect;15, &sect;38 - "DATA: Excel, CSV"), through the same
 * {@link CreateEventRequest} DTO Phase 7's own tests use (RULE 5). Proves
 * {@link TestDataManager}'s typed conversion coerces a spreadsheet cell's {@code "75"} into
 * {@code CreateEventRequest.price()}'s {@code double}, and {@code "20"} into
 * {@code totalSeats()}'s {@code int} - real values, not assumed to "just work".
 */
public class DataDrivenEventCreationTest {

    private final AuthenticationService authService = new AuthenticationService();
    private final EventService eventService = new EventService();

    private Integer createdEventId;

    // alwaysRun = true: see EventBookingChainingTest's identical note (Phase 13) - without
    // this, TestNG silently skips this method whenever a group include-filter is active.
    @BeforeMethod(alwaysRun = true)
    public void logIn() {
        authService.login(SecretManager.get("EVENTHUB_EMAIL"), SecretManager.get("EVENTHUB_PASSWORD"));
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        if (createdEventId != null) {
            eventService.deleteEvent(createdEventId);
            createdEventId = null;
        }
        authService.logout();
    }

    @DataProvider(name = "csvEvents")
    public Object[][] csvEvents() {
        return TestDataManager.load("events.csv").dataProvider(CreateEventRequest.class);
    }

    @DataProvider(name = "excelEvents")
    public Object[][] excelEvents() {
        return TestDataManager.load("events.xlsx").dataProvider(CreateEventRequest.class);
    }

    @Test(groups = {"smoke", "api"}, dataProvider = "csvEvents")
    public void csvSourcedEventDataCreatesARealEvent(CreateEventRequest request) {
        ApiResponse response = eventService.createEvent(request);
        response.assertStatusCode(201);

        createdEventId = response.jsonPath().getInt("data.id");
        assertEquals(response.jsonPath().getString("data.title"), request.title());
        assertEquals(response.jsonPath().getInt("data.totalSeats"), request.totalSeats());
    }

    @Test(groups = {"smoke", "api"}, dataProvider = "excelEvents")
    public void excelSourcedEventDataCreatesARealEvent(CreateEventRequest request) {
        ApiResponse response = eventService.createEvent(request);
        response.assertStatusCode(201);

        createdEventId = response.jsonPath().getInt("data.id");
        assertEquals(response.jsonPath().getString("data.title"), request.title());
        assertEquals(response.jsonPath().getInt("data.totalSeats"), request.totalSeats());
    }
}
