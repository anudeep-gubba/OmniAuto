package com.tests.base;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.WebDriverManager;
import com.framework.exceptions.ConfigurationException;
import com.framework.exceptions.DriverInitializationException;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 4 validation: real, live Chrome and Firefox headless sessions (both
 * installed in this environment - see Phase 4 summary), plus config-only
 * fail-fast checks that need no browser at all.
 *
 * <p>Edge and Safari are not live-tested here: Edge is not installed in this
 * environment, and Safari's WebDriver requires a one-time, machine-level
 * {@code safaridriver --enable} plus a manual "Allow Remote Automation"
 * toggle that a headless CI-style run cannot perform. Both go through the
 * exact same {@code Options}-based creation path as Chrome/Firefox; see the
 * Phase 4 summary for how this was verified instead.</p>
 */
public class DriverFactoryTest {

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        // DriverCleanupListener already quits any active driver after the test method;
        // this only resets the ConfigManager thread-local overrides used to pick a browser.
        ConfigManager.clearThreadState();
    }

    @Test(groups = "smoke")
    public void chromeHeadlessDriverLifecycleWorks() {
        ConfigManager.setOverride(ConfigKeys.BROWSER, "chrome");
        ConfigManager.setOverride(ConfigKeys.HEADLESS, "true");

        WebDriver driver = WebDriverManager.getDriver();
        driver.get("data:text/html,<html><body><h1 id='marker'>chrome-ok</h1></body></html>");

        assertTrue(driver.getPageSource().contains("chrome-ok"));
        assertTrue(WebDriverManager.isDriverActive());
    }

    @Test(groups = "smoke")
    public void firefoxHeadlessDriverLifecycleWorks() {
        ConfigManager.setOverride(ConfigKeys.BROWSER, "firefox");
        ConfigManager.setOverride(ConfigKeys.HEADLESS, "true");

        WebDriver driver = WebDriverManager.getDriver();
        driver.get("data:text/html,<html><body><h1 id='marker'>firefox-ok</h1></body></html>");

        assertTrue(driver.getPageSource().contains("firefox-ok"));
        assertTrue(WebDriverManager.isDriverActive());
    }

    @Test(groups = "smoke")
    public void resolutionIsAppliedToWindowSize() {
        ConfigManager.setOverride(ConfigKeys.BROWSER, "chrome");
        ConfigManager.setOverride(ConfigKeys.HEADLESS, "true");
        ConfigManager.setOverride(ConfigKeys.RESOLUTION, "1366x768");

        WebDriver driver = WebDriverManager.getDriver();
        Dimension size = driver.manage().window().getSize();

        assertEquals(size.getWidth(), 1366);
        assertEquals(size.getHeight(), 768);
    }

    @Test(groups = "smoke")
    public void unsupportedBrowserFailsFastWithoutTouchingSelenium() {
        ConfigManager.setOverride(ConfigKeys.BROWSER, "opera");

        ConfigurationException exception = expectThrows(ConfigurationException.class, WebDriverManager::getDriver);
        assertTrue(exception.getMessage().contains("opera"));
        assertFalse(WebDriverManager.isDriverActive());
    }

    @Test(groups = "smoke")
    public void invalidResolutionFailsFast() {
        ConfigManager.setOverride(ConfigKeys.BROWSER, "chrome");
        ConfigManager.setOverride(ConfigKeys.HEADLESS, "true");
        ConfigManager.setOverride(ConfigKeys.RESOLUTION, "not-a-resolution");

        DriverInitializationException exception =
                expectThrows(DriverInitializationException.class, WebDriverManager::getDriver);
        assertTrue(exception.getMessage().contains("not-a-resolution"));
    }
}
