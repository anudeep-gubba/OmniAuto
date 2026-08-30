package com.tests.steps.mobile;

import com.tests.application.components.mobile.EventCardComponent;
import com.tests.application.pages.mobile.EventDetailPage;
import com.tests.application.pages.mobile.HomePage;
import com.tests.steps.shared.MobileScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static com.framework.utils.Verify.assertEquals;
import static com.framework.utils.Verify.assertTrue;

/**
 * Steps behind {@code features/mobile/events.feature} - a mechanical lift of the old
 * {@code com.tests.tests.mobile.EventsTest} {@code @Test} method bodies into Given/When/Then
 * steps.
 */
public class EventsSteps {

    private final MobileScenarioContext context;
    private List<EventCardComponent> cards;

    public EventsSteps(MobileScenarioContext context) {
        this.context = context;
    }

    @Given("I am logged in and browsing events")
    public void iAmLoggedInAndBrowsingEvents() {
        HomePage homePage = context.ensureLoggedIn();
        assertTrue(homePage.isDisplayed(), "Login should complete before browsing events.");
        context.eventsPage = homePage.browseEvents();
    }

    @When("I read the mobile event cards")
    public void iReadTheEventCards() {
        cards = context.eventsPage.getEventCards();
    }

    @Then("the events listing should show at least one event")
    public void theEventsListingShouldShowAtLeastOneEvent() {
        assertTrue(cards.size() > 0, "Events listing should show at least one event.");
    }

    @Then("the first mobile event card should display a non-blank name and a dollar price")
    public void theFirstEventCardShouldDisplayANameAndPrice() {
        EventCardComponent first = cards.get(0);
        assertTrue(!first.getName().isBlank(), "First event card should display a non-blank name.");
        assertTrue(first.getPrice().startsWith("$"), "Price should be displayed as a dollar amount.");
    }

    @Then("every mobile event card should report its own distinct name")
    public void everyEventCardShouldReportItsOwnDistinctName() {
        assertTrue(cards.size() >= 2, "Test needs at least 2 events to prove independent scoping.");
        List<String> names = cards.stream().map(EventCardComponent::getName).toList();
        assertEquals(names.stream().distinct().count(), names.size(),
                "Every event card should report its own distinct name.");
    }

    @When("I tap Book Now on the first event card")
    public void iTapBookNowOnTheFirstEventCard() {
        cards = context.eventsPage.getEventCards();
        assertTrue(cards.size() > 0, "Events listing should show at least one event to tap Book Now on.");
        cards.get(0).tapBookNow();
    }

    @Then("the event detail page should be displayed")
    public void theEventDetailPageShouldBeDisplayed() {
        EventDetailPage detailPage = new EventDetailPage();
        assertTrue(detailPage.isDisplayed(), "Book Now should navigate to the event detail/booking screen.");
    }
}
