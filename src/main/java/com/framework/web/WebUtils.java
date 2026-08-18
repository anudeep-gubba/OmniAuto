package com.framework.web;

import com.framework.driver.WebDriverManager;
import com.framework.exceptions.ElementInteractionException;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Browser/page-level Web operations that are not tied to locating a specific
 * element: navigation, JavaScript execution, alerts, frames, and windows/tabs
 * (requirement.md &sect;5). Element-level interactions (click, type, select, ...)
 * live in {@link WebActions} instead.
 */
public final class WebUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebUtils.class);

    private WebUtils() {
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    public static void navigateTo(String url) {
        WebDriverManager.getDriver().get(url);
        LOGGER.info("Navigated to: {}", url);
    }

    public static void refresh() {
        WebDriverManager.getDriver().navigate().refresh();
        LOGGER.info("Refreshed the page.");
    }

    public static void navigateBack() {
        WebDriverManager.getDriver().navigate().back();
        LOGGER.info("Navigated back.");
    }

    public static void navigateForward() {
        WebDriverManager.getDriver().navigate().forward();
        LOGGER.info("Navigated forward.");
    }

    public static String getCurrentUrl() {
        return WebDriverManager.getDriver().getCurrentUrl();
    }

    public static String getTitle() {
        return WebDriverManager.getDriver().getTitle();
    }

    public static String getPageSource() {
        return WebDriverManager.getDriver().getPageSource();
    }

    // ------------------------------------------------------------------
    // JavaScript
    // ------------------------------------------------------------------

    public static Object executeScript(String script, Object... args) {
        Object result = ((JavascriptExecutor) WebDriverManager.getDriver()).executeScript(script, args);
        LOGGER.info("Executed JavaScript.");
        return result;
    }

    // ------------------------------------------------------------------
    // Alert handling
    // ------------------------------------------------------------------

    public static void acceptAlert() {
        alert().accept();
        LOGGER.info("Accepted alert.");
    }

    public static void dismissAlert() {
        alert().dismiss();
        LOGGER.info("Dismissed alert.");
    }

    public static String getAlertText() {
        return alert().getText();
    }

    private static Alert alert() {
        return WebWaits.waitFor(ExpectedConditions.alertIsPresent(), "alert to be present");
    }

    // ------------------------------------------------------------------
    // Iframe handling
    // ------------------------------------------------------------------

    public static void switchToFrame(By locator) {
        try {
            WebDriverManager.getDriver().switchTo().frame(WebWaits.waitForVisible(locator));
            LOGGER.info("Switched to frame: {}", locator);
        } catch (WebDriverException e) {
            throw new ElementInteractionException("Failed to switch to frame: " + locator, e);
        }
    }

    public static void switchToDefaultContent() {
        WebDriverManager.getDriver().switchTo().defaultContent();
        LOGGER.info("Switched to default content.");
    }

    // ------------------------------------------------------------------
    // Window / tab handling
    // ------------------------------------------------------------------

    public static void switchToWindow(String nameOrHandle) {
        WebDriverManager.getDriver().switchTo().window(nameOrHandle);
        LOGGER.info("Switched to window/tab: {}", nameOrHandle);
    }

    /**
     * Opens and switches to a new tab using Selenium's native
     * {@code switchTo().newWindow()}. Deliberately not JavaScript's
     * {@code window.open()}: that route was found, empirically, to hang
     * headless Chrome when switching into the resulting window (see Phase 5
     * summary) - a real bug this design sidesteps rather than merely avoids
     * by convention.
     */
    public static void openNewTab() {
        WebDriverManager.getDriver().switchTo().newWindow(WindowType.TAB);
        LOGGER.info("Opened and switched to a new tab.");
    }

    public static Set<String> getWindowHandles() {
        return WebDriverManager.getDriver().getWindowHandles();
    }

    public static String getCurrentWindowHandle() {
        return WebDriverManager.getDriver().getWindowHandle();
    }

    public static void closeCurrentWindow() {
        WebDriverManager.getDriver().close();
        LOGGER.info("Closed the current window/tab.");
    }
}
