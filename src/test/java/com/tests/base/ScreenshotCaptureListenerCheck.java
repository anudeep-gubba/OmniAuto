package com.tests.base;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.WebDriverManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.testng.Assert.assertTrue;

/**
 * Phase 5 validation: proves {@link com.framework.listeners.ScreenshotCaptureListener}
 * actually captures a PNG on failure - and, critically, that it runs
 * <em>before</em> {@link com.framework.listeners.DriverCleanupListener} quits
 * the driver (see that listener's Javadoc for why this ordering is not left
 * to chance). This test deliberately fails so the listener chain fires for
 * real, then a second method (run immediately after, same thread, default
 * non-parallel execution) checks a screenshot file actually landed on disk.
 *
 * <p>Deliberately <b>not</b> named {@code *Test}: it must genuinely FAIL
 * (status == {@code ITestResult.FAILURE}) for {@code ScreenshotCaptureListener}
 * to trigger - {@code expectedExceptions} would make TestNG mark it SUCCESS
 * instead, which would mean the listener never fires. A test that's supposed
 * to fail can't be part of the default regression suite, so this is run
 * explicitly instead, directly via the TestNG CLI against the compiled
 * classes (see Phase 5 summary for the exact command and result).</p>
 */
public class ScreenshotCaptureListenerCheck {

    private static final String MARKER = "screenshot-listener-ordering-check";

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ConfigManager.clearThreadState();
    }

    @Test(groups = "web", priority = 1)
    public void deliberatelyFailingTestWithAnActiveDriver() {
        ConfigManager.setOverride(ConfigKeys.BROWSER, "chrome");
        ConfigManager.setOverride(ConfigKeys.HEADLESS, "true");
        WebDriverManager.getDriver().get("data:text/html,<html><body>" + MARKER + "</body></html>");

        throw new AssertionError("Deliberate failure to exercise ScreenshotCaptureListener.");
    }

    @Test(groups = "web", priority = 2)
    public void previousFailureProducedAScreenshotFile() throws IOException {
        Path screenshotDir = Path.of("target", "screenshots");
        assertTrue(Files.isDirectory(screenshotDir), "target/screenshots should exist after a failure.");

        try (Stream<Path> files = Files.list(screenshotDir)) {
            boolean found = files.anyMatch(path ->
                    path.getFileName().toString().contains("deliberatelyFailingTestWithAnActiveDriver"));
            assertTrue(found, "Expected a screenshot file for the deliberately-failed test method. "
                    + "If missing, ScreenshotCaptureListener ran after DriverCleanupListener already quit the driver.");
        }
    }
}
