package com.tests.steps.api;

import com.tests.steps.shared.ApiScenarioContext;
import io.cucumber.java.en.Given;

/**
 * The handful of steps genuinely common to more than one API feature (logging in/out as the
 * seeded account) - kept in exactly one place so {@code events.feature}'s and {@code
 * bookings.feature}'s identical {@code Background} steps aren't defined twice across
 * {@link EventSteps}/{@link BookingSteps}, which {@code cucumber-java} would reject at runtime
 * as an ambiguous step definition.
 */
public class CommonApiSteps {

    private final ApiScenarioContext context;

    public CommonApiSteps(ApiScenarioContext context) {
        this.context = context;
    }

    @Given("I am logged in via the API as the seeded account")
    public void iAmLoggedInViaTheApiAsTheSeededAccount() {
        context.loginWithSeededAccount();
    }

    @Given("I am logged out")
    public void iAmLoggedOut() {
        context.authService.logout();
    }
}
