package com.tests.components.mobile;

import com.framework.mobile.BaseMobileComponent;
import com.framework.mobile.MobileActions;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * One repeated event card on the eventhub mobile app's Events listing, mirroring the Web
 * layer's {@code EventCardComponent}. Unlike Web, this Flutter build exposes each card as a
 * single leaf element whose own accessibility label is the whole card's text glued together
 * with newlines - verified live: {@code "Concert\nIndie Music Night\nSat, 14 Nov\nThe Blue
 * Note Lounge, Austin\n$45.00\n128 seats left!"} - rather than one sub-element per field, so
 * the getters below parse that text instead of locating children.
 *
 * <p>The "Book Now" button is a <i>sibling</i> of the card, not a descendant of it (verified
 * live), so it can't be reached via {@link BaseMobileComponent}'s root-scoped {@code find()}
 * (which only ever searches descendants). {@link #tapBookNow()} instead indexes into the
 * screen's own flat list of "Book Now" buttons at this card's position, which
 * {@code EventsPage#getEventCards()} guarantees lines up 1:1 with the card list.</p>
 */
public class EventCardComponent extends BaseMobileComponent {

    private static final By BOOK_NOW_BUTTON = AppiumBy.accessibilityId("Book Now");

    private final int index;

    public EventCardComponent(WebElement root, int index) {
        super(root);
        this.index = index;
    }

    public String getName() {
        return lines()[1];
    }

    public String getPrice() {
        String[] lines = lines();
        return lines[lines.length - 2];
    }

    public String getSeatsLeftText() {
        String[] lines = lines();
        return lines[lines.length - 1];
    }

    public void tapBookNow() {
        String name = getName();
        List<WebElement> bookButtons = MobileActions.findAll(BOOK_NOW_BUTTON);
        bookButtons.get(index).click();
        logger.info("Tapped 'Book Now' on '{}'", name);
    }

    private String[] lines() {
        return root().getText().split("\n");
    }
}
