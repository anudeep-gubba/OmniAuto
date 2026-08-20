package com.tests.tests.mobile;

import com.tests.application.base.BaseMobileTest;
import com.tests.application.components.mobile.EventCardComponent;
import com.tests.application.pages.mobile.EventDetailPage;
import com.tests.application.pages.mobile.EventsPage;
import com.tests.application.pages.mobile.HomePage;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Mobile Component Object Model coverage for the eventhub app's Events listing:
 * {@link com.tests.application.components.mobile.HeaderComponent} as a page-wide singleton component,
 * {@link EventCardComponent} as an N-repeated component - mirroring the Web layer's
 * {@code com.tests.tests.web.EventsTest} on the real screen, verified live against the iPhone 17
 * Pro Simulator.
 */
public class EventsTest extends BaseMobileTest {

    private EventsPage eventsPage;

    // alwaysRun = true: without this, TestNG silently skips this method whenever a group
    // include-filter (-Dgroups=...) is active, even though the @Test method it sets up for
    // still runs - producing a confusing failure (an unauthenticated screen) rather than an
    // obvious "setup didn't run".
    @BeforeMethod(alwaysRun = true)
    public void logInAndBrowseEvents() {
        HomePage homePage = ensureLoggedIn();
        assertTrue(homePage.isDisplayed(), "Login should complete before browsing events.");

        eventsPage = homePage.browseEvents();
    }

    @Test(groups = "mobile")
    public void eventsListingShowsAtLeastOneEvent() {
        List<EventCardComponent> cards = eventsPage.getEventCards();
        assertTrue(cards.size() > 0, "Events listing should show at least one event.");

        EventCardComponent first = cards.get(0);
        assertTrue(!first.getName().isBlank());
        assertTrue(first.getPrice().startsWith("$"), "Price should be displayed as a dollar amount.");
    }

    @Test(groups = "mobile")
    public void eachEventCardIsIndependentlyScoped() {
        // Proves BaseMobileComponent's root-scoping: reading N cards' names never returns the
        // same element twice or bleeds one card's data into another's.
        List<EventCardComponent> cards = eventsPage.getEventCards();
        assertTrue(cards.size() >= 2, "Test needs at least 2 events to prove independent scoping.");

        List<String> names = cards.stream().map(EventCardComponent::getName).toList();
        assertEquals(names.stream().distinct().count(), names.size(),
                "Every event card should report its own distinct name.");
    }

    @Test(groups = "mobile")
    public void bookNowNavigatesToEventDetailPage() {
        List<EventCardComponent> cards = eventsPage.getEventCards();
        assertTrue(cards.size() > 0);

        cards.get(0).tapBookNow();

        EventDetailPage detailPage = new EventDetailPage();
        assertTrue(detailPage.isDisplayed(), "Book Now should navigate to the event detail/booking screen.");
    }
}
