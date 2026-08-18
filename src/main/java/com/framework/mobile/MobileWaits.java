package com.framework.mobile;

import com.framework.driver.MobileDriverManager;
import com.framework.exceptions.ElementInteractionException;
import com.framework.utils.WaitUtils;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.function.Function;

/**
 * The single centralized explicit-wait engine for Mobile automation, mirroring
 * {@link com.framework.web.WebWaits} (requirement.md &sect;5: "All waits must
 * be centralized"; RULE 8: never {@code Thread.sleep()}). Timeout/polling
 * configuration is the exact same {@link WaitUtils} both platforms share, so
 * one config change tunes Web and Mobile waits together.
 */
public final class MobileWaits {

    private MobileWaits() {
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

    /** Escape hatch for any {@link ExpectedConditions} (or custom condition) not covered by a named method above. */
    public static <T> T waitFor(Function<org.openqa.selenium.WebDriver, T> condition, String description) {
        try {
            AppiumDriver driver = MobileDriverManager.getDriver();
            return WaitUtils.buildFluentWait(driver).until(condition);
        } catch (TimeoutException e) {
            throw new ElementInteractionException("Timed out waiting for " + description, e);
        }
    }
}
