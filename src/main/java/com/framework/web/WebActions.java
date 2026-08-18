package com.framework.web;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.WebDriverManager;
import com.framework.enums.ScreenshotMode;
import com.framework.exceptions.ElementInteractionException;
import com.framework.utils.ScreenshotUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Element-level Web interactions: every method takes a {@link By} locator,
 * waits for it via {@link WebWaits} first (never a raw, unwaited
 * {@code driver.findElement(...)} - RULE 12), then acts and logs the action
 * (requirement.md &sect;16/&sect;18). Selenium exceptions are wrapped as
 * {@link ElementInteractionException} so callers only ever catch one type.
 *
 * <p>Browser/page-level operations that are not tied to a specific element
 * (navigation, JavaScript execution, alerts, frames, windows) live in
 * {@link WebUtils} instead.</p>
 */
public final class WebActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebActions.class);

    private WebActions() {
    }

    public static void click(By locator) {
        try {
            WebElement element = WebWaits.waitForClickable(locator);
            clickResiliently(element, locator.toString());
            LOGGER.info("Clicked element: {}", locator);
            maybeScreenshotAfterAction("click");
        } catch (ElementInteractionException e) {
            throw e;
        } catch (WebDriverException e) {
            throw new ElementInteractionException("Failed to click element: " + locator, e);
        }
    }

    public static void type(By locator, String value) {
        try {
            WebElement element = WebWaits.waitForVisible(locator);
            element.clear();
            element.sendKeys(value);
            LOGGER.info("Typed into element: {}", locator);
            maybeScreenshotAfterAction("type");
        } catch (ElementInteractionException e) {
            throw e;
        } catch (WebDriverException e) {
            throw new ElementInteractionException("Failed to type into element: " + locator, e);
        }
    }

    public static String getText(By locator) {
        String text = WebWaits.waitForVisible(locator).getText();
        LOGGER.info("Read text from element {}: '{}'", locator, text);
        return text;
    }

    public static boolean isDisplayed(By locator) {
        try {
            return WebWaits.waitForVisible(locator).isDisplayed();
        } catch (ElementInteractionException e) {
            return false;
        }
    }

    public static List<WebElement> findAll(By locator) {
        List<WebElement> elements = WebDriverManager.getDriver().findElements(locator);
        LOGGER.info("Found {} element(s) matching: {}", elements.size(), locator);
        return elements;
    }

    // ------------------------------------------------------------------
    // Dropdown handling
    // ------------------------------------------------------------------

    public static void selectByVisibleText(By locator, String visibleText) {
        new Select(WebWaits.waitForVisible(locator)).selectByVisibleText(visibleText);
        LOGGER.info("Selected '{}' in dropdown: {}", visibleText, locator);
        maybeScreenshotAfterAction("selectByVisibleText");
    }

    public static void selectByValue(By locator, String value) {
        new Select(WebWaits.waitForVisible(locator)).selectByValue(value);
        LOGGER.info("Selected value '{}' in dropdown: {}", value, locator);
        maybeScreenshotAfterAction("selectByValue");
    }

    // ------------------------------------------------------------------
    // Keyboard / mouse
    // ------------------------------------------------------------------

    public static void hover(By locator) {
        WebElement element = WebWaits.waitForVisible(locator);
        new Actions(WebDriverManager.getDriver()).moveToElement(element).perform();
        LOGGER.info("Hovered over element: {}", locator);
        maybeScreenshotAfterAction("hover");
    }

    public static void pressEnter(By locator) {
        WebWaits.waitForVisible(locator).sendKeys(org.openqa.selenium.Keys.ENTER);
        LOGGER.info("Pressed ENTER on element: {}", locator);
        maybeScreenshotAfterAction("pressEnter");
    }

    // ------------------------------------------------------------------
    // File upload
    // ------------------------------------------------------------------

    public static void uploadFile(By locator, String absoluteFilePath) {
        WebWaits.waitForVisible(locator).sendKeys(absoluteFilePath);
        LOGGER.info("Uploaded file via element: {}", locator);
        maybeScreenshotAfterAction("uploadFile");
    }

    // ------------------------------------------------------------------
    // Scrolling
    // ------------------------------------------------------------------

    public static void scrollIntoView(By locator) {
        WebElement element = WebWaits.waitForVisible(locator);
        WebUtils.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
        LOGGER.info("Scrolled element into view: {}", locator);
    }

    private static void maybeScreenshotAfterAction(String actionName) {
        ScreenshotMode mode = ScreenshotMode.fromString(ConfigManager.getString(ConfigKeys.SCREENSHOT_MODE, "FAILURE"));
        if (mode == ScreenshotMode.EVERY_ACTION) {
            ScreenshotUtils.capture(WebDriverManager.getDriver(), actionName);
        }
    }

    /**
     * Clicks an already-located, already-clickable element, retrying through
     * {@link ElementClickInterceptedException} - which {@code elementToBeClickable}
     * does not prevent, since it only checks visible+enabled, not "nothing
     * currently overlaps it at that point." A sticky header, toast, or
     * animation-in-progress intercepting a click is common enough on real SPAs
     * (found on eventhub.rahulshettyacademy.com's own login button in headless
     * mode - see Phase 5 summary) to handle once here, shared by both
     * {@link #click(By)} and {@link BaseComponent#click(By)}, rather than in
     * every caller. Scroll-then-retry first; a JS click is the final fallback
     * since it bypasses the browser's hit-testing entirely.
     */
    static void clickResiliently(WebElement element, String description) {
        try {
            element.click();
        } catch (ElementClickInterceptedException e) {
            LOGGER.warn("Click intercepted for {}, retrying after scrollIntoView.", description);
            WebUtils.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
            try {
                element.click();
            } catch (ElementClickInterceptedException stillIntercepted) {
                LOGGER.warn("Click still intercepted for {}, falling back to a JavaScript click.", description);
                WebUtils.executeScript("arguments[0].click();", element);
            }
        }
    }
}
