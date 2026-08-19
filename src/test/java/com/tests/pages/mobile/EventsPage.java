package com.tests.pages.mobile;

import com.framework.mobile.BaseMobilePage;
import com.framework.mobile.MobileActions;
import com.framework.mobile.PlatformLocator;
import com.tests.components.mobile.EventCardComponent;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;

/**
 * The eventhub mobile app's full event listing, mirroring the Web layer's {@code EventsPage}.
 * Reached via {@link HomePage#browseEvents()}.
 */
public class EventsPage extends BaseMobilePage {

    private static final By PAGE_TITLE = AppiumBy.accessibilityId("Events");
    // Each card is a leaf element whose composite label always ends in "seats left!" - verified
    // live - rather than carrying its own dedicated accessibility id. The element class name
    // that label lives under is platform-specific (Flutter renders its own widgets, not native
    // ones): XCUIElementTypeOther's "name" on iOS, android.view.View's "content-desc" on
    // Android - see PlatformLocator.
    private static final By EVENT_CARD = PlatformLocator.of(
            By.xpath("//android.view.View[contains(@content-desc,'seats left')]"),
            By.xpath("//XCUIElementTypeOther[contains(@name,'seats left')]"));

    public boolean isDisplayed() {
        return isDisplayed(PAGE_TITLE);
    }

    public List<EventCardComponent> getEventCards() {
        List<WebElement> roots = MobileActions.findAll(EVENT_CARD);
        List<EventCardComponent> cards = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            cards.add(new EventCardComponent(roots.get(i), i));
        }
        logger.info("Events listing shows {} event(s)", cards.size());
        return cards;
    }
}
