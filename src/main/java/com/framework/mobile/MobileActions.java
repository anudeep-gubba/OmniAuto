package com.framework.mobile;

import com.framework.driver.MobileDriverManager;
import com.framework.exceptions.ElementInteractionException;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Interactive;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Element-level Mobile interactions, mirroring {@link com.framework.web.WebActions}:
 * every method takes a {@link By} locator, waits for it via {@link MobileWaits}
 * first (never a raw, unwaited find - RULE 12), then acts and logs the action
 * (requirement.md &sect;16/&sect;18). Appium/Selenium exceptions are wrapped as
 * {@link ElementInteractionException} so callers only ever catch one type.
 *
 * <p>Gestures use W3C {@link PointerInput} actions, not the deprecated
 * {@code TouchAction} API (still present in {@code java-client} 9.x for
 * backward compatibility, but superseded).</p>
 *
 * <p>Device/app-level operations that are not tied to a specific element
 * (keyboard, app lifecycle, screen-relative swipes) live in
 * {@link MobileUtils} instead.</p>
 */
public final class MobileActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileActions.class);
    private static final Duration SWIPE_DURATION = Duration.ofMillis(400);
    private static final int MAX_SCROLL_ATTEMPTS = 8;

    private MobileActions() {
    }

    public static void tap(By locator) {
        try {
            MobileWaits.waitForClickable(locator).click();
            LOGGER.info("Tapped element: {}", locator);
        } catch (ElementInteractionException e) {
            throw e;
        } catch (WebDriverException e) {
            throw new ElementInteractionException("Failed to tap element: " + locator, e);
        }
    }

    public static void type(By locator, String value) {
        try {
            WebElement element = MobileWaits.waitForVisible(locator);
            element.clear();
            element.sendKeys(value);
            LOGGER.info("Typed into element: {}", locator);
        } catch (ElementInteractionException e) {
            throw e;
        } catch (WebDriverException e) {
            throw new ElementInteractionException("Failed to type into element: " + locator, e);
        }
    }

    public static String getText(By locator) {
        String text = MobileWaits.waitForVisible(locator).getText();
        LOGGER.info("Read text from element {}: '{}'", locator, text);
        return text;
    }

    public static boolean isDisplayed(By locator) {
        try {
            return MobileWaits.waitForVisible(locator).isDisplayed();
        } catch (ElementInteractionException e) {
            return false;
        }
    }

    public static List<WebElement> findAll(By locator) {
        List<WebElement> elements = MobileDriverManager.getDriver().findElements(locator);
        LOGGER.info("Found {} element(s) matching: {}", elements.size(), locator);
        return elements;
    }

    public static void longPress(By locator) {
        WebElement element = MobileWaits.waitForVisible(locator);
        Dimension size = element.getSize();
        org.openqa.selenium.Point location = element.getLocation();
        int centerX = location.getX() + size.getWidth() / 2;
        int centerY = location.getY() + size.getHeight() / 2;

        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence longPress = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), centerX, centerY))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new org.openqa.selenium.interactions.Pause(finger, Duration.ofMillis(800)))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        perform(longPress);
        LOGGER.info("Long-pressed element: {}", locator);
    }

    /**
     * Swipes up the screen (searching downward through a scrollable list) up to
     * {@value #MAX_SCROLL_ATTEMPTS} times until {@code locator} is visible.
     */
    public static WebElement scrollToElement(By locator) {
        for (int attempt = 1; attempt <= MAX_SCROLL_ATTEMPTS; attempt++) {
            List<WebElement> matches = MobileDriverManager.getDriver().findElements(locator);
            if (!matches.isEmpty() && matches.get(0).isDisplayed()) {
                LOGGER.info("Found element {} after {} scroll attempt(s).", locator, attempt - 1);
                return matches.get(0);
            }
            MobileUtils.swipeUp();
        }
        throw new ElementInteractionException(
                "Element '" + locator + "' was not found after " + MAX_SCROLL_ATTEMPTS + " scroll attempts.");
    }

    static void perform(Sequence sequence) {
        ((Interactive) MobileDriverManager.getDriver()).perform(List.of(sequence));
    }
}
