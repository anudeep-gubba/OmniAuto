package com.framework.driver;

import org.openqa.selenium.WebDriver;

/**
 * Public entry point for Web driver access. Lazily creates a driver for the
 * calling thread on first use &mdash; "Thread 1 -&gt; Chrome Driver 1, Thread 2
 * -&gt; Chrome Driver 2" (requirement.md &sect;20) &mdash; and must be paired with
 * {@link #quitDriver()} on test completion (requirement.md &sect;33). In
 * practice, {@link com.framework.listeners.DriverCleanupListener} calls
 * {@link #quitDriver()} automatically after every test method, so page
 * objects and tests only ever call {@link #getDriver()}.
 */
public final class WebDriverManager {

    private WebDriverManager() {
    }

    public static WebDriver getDriver() {
        WebDriver driver = DriverManager.getWebDriverOrNull();
        if (driver == null) {
            driver = DriverFactory.createWebDriver();
            DriverManager.setWebDriver(driver);
        }
        return driver;
    }

    public static boolean isDriverActive() {
        return DriverManager.getWebDriverOrNull() != null;
    }

    public static void quitDriver() {
        DriverManager.quitWebDriver();
    }
}
