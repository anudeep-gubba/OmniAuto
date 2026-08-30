package com.tests.steps.web;

import com.framework.config.ConfigManager;
import com.framework.secrets.SecretManager;
import com.framework.web.WebUtils;
import com.framework.web.WebWaits;
import com.tests.application.components.web.EventCardComponent;
import com.tests.application.pages.web.EventsPage;
import com.tests.steps.shared.WebScenarioContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertTrue;

/**
 * Steps behind {@code features/web/events.feature} - a mechanical lift of the old
 * {@code com.tests.tests.web.EventsTest} {@code @Test} method bodies into Given/When/Then steps.
 */
public class EventsSteps {

    private final WebScenarioContext context;
    private List<EventCardComponent> cards;

    public EventsSteps(WebScenarioContext context) {
        this.context = context;
    }

    @Given("I am logged in as the seeded account")
    public void iAmLoggedInAsTheSeededAccount() {
        context.loginWithSeededAccount();
    }

    @And("I navigate to the events page")
    public void iNavigateToTheEventsPage() {
        WebUtils.navigateTo(ConfigManager.getBaseUrl() + "/events");
        context.eventsPage = new EventsPage();
    }

    @When("I read the event cards")
    public void iReadTheEventCards() {
        cards = context.eventsPage.getEventCards();
    }

    @Then("the events page should list at least one event")
    public void theEventsPageShouldListAtLeastOneEvent() {
        assertTrue(cards.size() > 0, "Events page should list at least one event.");
    }

    @And("the first event card should display a non-blank name and a dollar price")
    public void theFirstEventCardShouldDisplayANameAndPrice() {
        EventCardComponent first = cards.get(0);
        assertTrue(!first.getName().isBlank(), "First event card should display a non-blank name.");
        assertTrue(first.getPrice().startsWith("$"), "Price should be displayed as a dollar amount.");
    }

    @Then("every event card should report its own distinct name")
    public void everyEventCardShouldReportItsOwnDistinctName() {
        assertTrue(cards.size() >= 2, "Test needs at least 2 events to prove independent scoping.");
        List<String> names = cards.stream().map(EventCardComponent::getName).toList();
        assertEquals(names.stream().distinct().count(), names.size(),
                "Every event card should report its own distinct name.");
    }

    @When("I click Book Now on the first event card")
    public void iClickBookNowOnTheFirstEventCard() {
        cards = context.eventsPage.getEventCards();
        assertTrue(cards.size() > 0, "Events page should list at least one event to click Book Now on.");
        cards.get(0).clickBookNow();
    }

    @Then("I should be navigated to an event detail page")
    public void iShouldBeNavigatedToAnEventDetailPage() {
        // Book Now is a Next.js client-side route transition, not a full page load: click()
        // returns as soon as the DOM event fires, before the URL updates. A bare getCurrentUrl()
        // right after would race it; wait for the URL instead.
        WebWaits.waitForUrlContains("/events/");
        assertTrue(WebUtils.getCurrentUrl().contains("/events/"),
                "Book Now should navigate to an event detail page, was: " + WebUtils.getCurrentUrl());
    }

    @Then("the header should show the logged-in user's email")
    public void theHeaderShouldShowTheLoggedInUsersEmail() {
        assertTrue(context.eventsPage.header().isLoggedIn(), "Header should show a logged-in state on the events page.");
        assertEquals(context.eventsPage.header().getLoggedInUserEmail(), SecretManager.get("EVENTHUB_EMAIL"),
                "Header should display the email of the account that's logged in.");
    }
}
