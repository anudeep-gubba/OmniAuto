package com.tests.hooks;

import com.framework.api.ApiContext;
import com.tests.steps.shared.ApiScenarioContext;
import io.cucumber.java.After;

/**
 * Runs after every {@code @api} scenario, regardless of outcome - the Cucumber-hook equivalent
 * of the old {@code BaseApiTest.baseApiCleanup()} {@code @AfterMethod}: release any event/
 * booking a scenario created (mirroring every {@code BaseApiTest} subclass's own
 * {@code tearDownTestData()} override, generalized here via {@link ApiScenarioContext}'s shared
 * fields instead of duplicated per class), clear {@code ApiContext}, then always log out -
 * a safe no-op when there is nothing to clear (see {@link com.tests.application.services.AuthenticationService#logout()}).
 */
public class ApiHooks {

    private final ApiScenarioContext context;

    public ApiHooks(ApiScenarioContext context) {
        this.context = context;
    }

    @After("@api")
    public void tearDownApiTestData() {
        if (context.createdBookingId != null) {
            context.bookingService.cancelBooking(context.createdBookingId);
            context.createdBookingId = null;
        }
        if (context.createdEventId != null) {
            context.eventService.deleteEvent(context.createdEventId);
            context.createdEventId = null;
        }
        ApiContext.clear();
        context.authService.logout();
    }
}
