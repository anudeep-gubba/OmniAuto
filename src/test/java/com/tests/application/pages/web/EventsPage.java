package com.tests.application.pages.web;

import com.framework.web.BasePage;
import com.framework.web.WebActions;
import com.framework.web.WebWaits;
import com.tests.application.components.web.EventCardComponent;
import com.tests.application.components.web.HeaderComponent;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * eventhub.rahulshettyacademy.com's full event listing (requirement.md
 * &sect;6 example: {@code ProductPage} - events here, matching what this
 * real application actually lists).
 */
public class EventsPage extends BasePage {

    private static final By EVENT_CARDS = By.cssSelector("[data-testid='event-card']");

    public HeaderComponent header() {
        return new HeaderComponent();
    }

    public List<EventCardComponent> getEventCards() {
        WebWaits.waitForVisible(EVENT_CARDS);
        List<WebElement> roots = WebActions.findAll(EVENT_CARDS);
        List<EventCardComponent> cards = roots.stream().map(EventCardComponent::new).toList();
        logger.info("Events listing shows {} event(s)", cards.size());
        return cards;
    }
}
