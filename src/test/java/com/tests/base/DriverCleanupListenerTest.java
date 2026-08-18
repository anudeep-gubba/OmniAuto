package com.tests.base;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Phase 4 validation: proves {@link com.framework.listeners.DriverCleanupListener}
 * quits a driver automatically, with no manual {@code quitDriver()} call in
 * test code. Method 1 deliberately leaves an active driver behind; method 2
 * (run immediately after, via {@code priority}, on the same thread under
 * default non-parallel execution) checks it is already gone.
 */
public class DriverCleanupListenerTest {

    @Test(groups = "smoke", priority = 1)
    public void createsDriverAndDeliberatelyDoesNotQuitItManually() {
        ConfigManager.setOverride(ConfigKeys.BROWSER, "chrome");
        ConfigManager.setOverride(ConfigKeys.HEADLESS, "true");

        WebDriver driver = WebDriverManager.getDriver();
        driver.get("data:text/html,<html><body>cleanup-listener-check</body></html>");

        assertTrue(WebDriverManager.isDriverActive());
        // No WebDriverManager.quitDriver() call here on purpose: DriverCleanupListener must do it.
    }

    @Test(groups = "smoke", priority = 2)
    public void previousMethodsDriverWasAlreadyQuitByTheListener() {
        assertFalse(WebDriverManager.isDriverActive(),
                "DriverCleanupListener should have quit and cleared the previous test method's WebDriver "
                        + "before this method ran.");
    }

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ConfigManager.clearThreadState();
    }
}
