package com.tests.tests.web;

import com.framework.config.ConfigManager;
import com.framework.secrets.SecretManager;
import com.framework.web.WebUtils;
import com.framework.web.WebWaits;
import com.tests.application.base.BaseWebTest;
import com.tests.application.components.web.EventCardComponent;
import com.tests.application.pages.web.EventsPage;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Phase 5 validation for the Component Object Model (requirement.md &sect;7):
 * {@link com.tests.application.components.web.HeaderComponent} as a page-wide singleton
 * component, {@link EventCardComponent} as an N-repeated component, on
 * eventhub.rahulshettyacademy.com's real event listing.
 */
public class EventsTest extends BaseWebTest {

    private EventsPage eventsPage;

    // alwaysRun = true: found in practice (Phase 13) - TestNG silently skips a @BeforeMethod
    // that lacks this when a group include-filter is active (-Dgroups=smoke), even though the
    // @Test method it sets up for still runs, producing confusing failures (here, an
    // unauthenticated call) rather than an obvious "setup didn't run". See CI_CD.md.
    @BeforeMethod(alwaysRun = true)
    public void logInAndGoToEvents() {
        loginWithSeededAccount();

        WebUtils.navigateTo(ConfigManager.getBaseUrl() + "/events");
        eventsPage = new EventsPage();
    }

    @Test(groups = "web")
    public void eventsListingShowsAtLeastOneEvent() {
        List<EventCardComponent> cards = eventsPage.getEventCards();
        assertTrue(cards.size() > 0, "Events page should list at least one event.");

        EventCardComponent first = cards.get(0);
        assertTrue(!first.getName().isBlank());
        assertTrue(first.getPrice().startsWith("$"), "Price should be displayed as a dollar amount.");
    }

    @Test(groups = "web")
    public void eachEventCardIsIndependentlyScoped() {
        // Proves BaseComponent's root-scoping: reading N cards' names never returns
        // the same element twice or bleeds one card's data into another's.
        List<EventCardComponent> cards = eventsPage.getEventCards();
        assertTrue(cards.size() >= 2, "Test needs at least 2 events to prove independent scoping.");

        List<String> names = cards.stream().map(EventCardComponent::getName).toList();
        assertEquals(names.stream().distinct().count(), names.size(),
                "Every event card should report its own distinct name.");
    }

    @Test(groups = "web")
    public void bookNowNavigatesToTheEventDetailPage() {
        List<EventCardComponent> cards = eventsPage.getEventCards();
        assertTrue(cards.size() > 0);

        cards.get(0).clickBookNow();

        // Book Now is a Next.js client-side route transition, not a full page load:
        // click() returns as soon as the DOM event fires, before the URL updates.
        // A bare getCurrentUrl() right after would race it; wait for the URL instead.
        WebWaits.waitForUrlContains("/events/");
        assertTrue(WebUtils.getCurrentUrl().contains("/events/"),
                "Book Now should navigate to an event detail page, was: " + WebUtils.getCurrentUrl());
    }

    @Test(groups = "web")
    public void headerShowsLoggedInUserAcrossPages() {
        assertTrue(eventsPage.header().isLoggedIn());
        assertEquals(eventsPage.header().getLoggedInUserEmail(), SecretManager.get("EVENTHUB_EMAIL"));
    }
}
