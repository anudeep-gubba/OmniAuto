package com.framework.driver;

import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-local storage and cleanup for the current thread's {@link WebDriver}
 * and {@link AppiumDriver}.
 *
 * <p><b>Thread-safety classification (requirement.md &sect;21):</b> both fields
 * are <b>thread-local</b> (category 3) by design &mdash; a driver instance is
 * never shared across threads (RULE 9: no global static WebDriver; RULE 10:
 * no shared mutable state for parallel tests). "Thread 1 -&gt; Chrome Driver 1,
 * Thread 2 -&gt; Chrome Driver 2" (requirement.md &sect;20) falls out naturally
 * from this.</p>
 *
 * <p>Deliberately package-private: {@link WebDriverManager} and
 * {@link MobileDriverManager} are the only public entry points, so lifecycle
 * logic (lazy creation, cleanup) lives in exactly one place per driver type
 * instead of being duplicated across page objects or test classes.</p>
 */
final class DriverManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DriverManager.class);

    private static final ThreadLocal<WebDriver> WEB_DRIVER = new ThreadLocal<>();
    private static final ThreadLocal<AppiumDriver> MOBILE_DRIVER = new ThreadLocal<>();

    private DriverManager() {
    }

    static void setWebDriver(WebDriver driver) {
        WEB_DRIVER.set(driver);
    }

    static WebDriver getWebDriverOrNull() {
        return WEB_DRIVER.get();
    }

    static void quitWebDriver() {
        WebDriver driver = WEB_DRIVER.get();
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (RuntimeException e) {
            LOGGER.warn("Error while quitting WebDriver on thread '{}': {}",
                    Thread.currentThread().getName(), e.getMessage());
        } finally {
            WEB_DRIVER.remove();
        }
    }

    static void setMobileDriver(AppiumDriver driver) {
        MOBILE_DRIVER.set(driver);
    }

    static AppiumDriver getMobileDriverOrNull() {
        return MOBILE_DRIVER.get();
    }

    static void quitMobileDriver() {
        AppiumDriver driver = MOBILE_DRIVER.get();
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (RuntimeException e) {
            LOGGER.warn("Error while quitting AppiumDriver on thread '{}': {}",
                    Thread.currentThread().getName(), e.getMessage());
        } finally {
            MOBILE_DRIVER.remove();
        }
    }
}
