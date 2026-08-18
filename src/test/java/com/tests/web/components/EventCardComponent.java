package com.tests.web.components;

import com.framework.web.BaseComponent;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * One repeated event card on eventhub.rahulshettyacademy.com's events
 * listing (requirement.md &sect;7 example: {@code ProductCard} - an event
 * card here, matching what this real application actually lists).
 * {@link com.tests.web.pages.HomePage} hands each instance an already-located
 * root element, so N cards never collide with each other's data.
 */
public class EventCardComponent extends BaseComponent {

    private static final By NAME = By.tagName("h3");
    private static final By PRICE = By.cssSelector(".text-indigo-700");
    private static final By SEATS_LEFT = By.cssSelector(".text-amber-600");
    private static final By BOOK_NOW_BUTTON = By.cssSelector("[data-testid='book-now-btn']");

    public EventCardComponent(WebElement root) {
        super(root);
    }

    public String getName() {
        return textOf(NAME);
    }

    public String getPrice() {
        return textOf(PRICE);
    }

    public String getSeatsLeftText() {
        return textOf(SEATS_LEFT);
    }

    public void clickBookNow() {
        // Capture the name before clicking: Book Now navigates to a detail page,
        // so the h3 this card's root pointed at won't exist anymore afterward.
        String name = getName();
        click(BOOK_NOW_BUTTON);
        logger.info("Clicked 'Book Now' on '{}'", name);
    }
}
