package com.framework.web;

import com.framework.driver.WebDriverManager;
import com.framework.exceptions.ElementInteractionException;
import com.framework.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.function.Function;

/**
 * The single centralized explicit-wait engine for Web automation
 * (requirement.md &sect;5: "All waits must be centralized"; RULE 8: never
 * {@code Thread.sleep()}). Timeout/polling configuration lives in
 * {@link WaitUtils}, shared with {@link com.framework.mobile.MobileWaits}.
 */
public final class WebWaits {

    private WebWaits() {
    }

    public static WebElement waitForVisible(By locator) {
        return waitFor(ExpectedConditions.visibilityOfElementLocated(locator), "element to be visible: " + locator);
    }

    public static WebElement waitForClickable(By locator) {
        return waitFor(ExpectedConditions.elementToBeClickable(locator), "element to be clickable: " + locator);
    }

    public static boolean waitForInvisible(By locator) {
        return waitFor(ExpectedConditions.invisibilityOfElementLocated(locator), "element to become invisible: " + locator);
    }

    public static boolean waitForUrlContains(String fragment) {
        return waitFor(ExpectedConditions.urlContains(fragment), "URL to contain: " + fragment);
    }

    public static boolean waitForTitleContains(String fragment) {
        return waitFor(ExpectedConditions.titleContains(fragment), "title to contain: " + fragment);
    }

    /** Escape hatch for any {@link ExpectedConditions} (or custom condition) not covered by a named method above. */
    public static <T> T waitFor(Function<WebDriver, T> condition, String description) {
        try {
            return WaitUtils.buildFluentWait(WebDriverManager.getDriver()).until(condition);
        } catch (TimeoutException e) {
            throw new ElementInteractionException("Timed out waiting for " + description, e);
        }
    }
}
