package com.framework.listeners;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.MobileDriverManager;
import com.framework.driver.WebDriverManager;
import com.framework.enums.ScreenshotMode;
import com.framework.reporting.ReportManager;
import com.framework.utils.ScreenshotUtils;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.nio.file.Path;

/**
 * Captures a screenshot when a test fails and {@code screenshot.mode} is
 * {@code FAILURE} or {@code EVERY_ACTION}; {@code DISABLED} skips capture
 * entirely (requirement.md &sect;19). Only fires if a Web or Mobile driver is
 * actually active - an API-only test's failure won't try to screenshot a
 * nonexistent browser.
 *
 * <p><b>Ordering matters here and is not left to chance:</b> this uses
 * {@link IInvokedMethodListener}, the same hook type as
 * {@link DriverCleanupListener}, and is registered <em>after</em> it in
 * {@code META-INF/services/org.testng.ITestNGListener}. TestNG invokes
 * multiple {@code afterInvocation} listeners in <b>reverse</b> registration
 * order (confirmed empirically with a two-listener probe, not assumed - an
 * initial "first registered, first invoked" assumption was wrong and
 * actually broke this listener silently, since {@code DriverCleanupListener}
 * ran first and had already quit the driver by the time this one checked
 * {@code isDriverActive()}; see Phase 5 summary). Listing
 * {@code DriverCleanupListener} first in the services file means it fires
 * <em>last</em>, after this one. If this instead used
 * {@code ITestListener.onTestFailure}, there would be no such ordering
 * guarantee available at all against a different hook type.</p>
 *
 * <p>Saves the PNG via {@link ScreenshotUtils}, then attaches it to both Extent (the still
 * -open node for this test - see {@link ExtentReportingListener}'s javadoc for why this must
 * run before that listener's own {@code afterInvocation}) and Allure via
 * {@link ReportManager#attachScreenshot(Path, String)} (Phase 11), requirement.md &sect;19:
 * "Screenshots should automatically attach to Extent and Allure."</p>
 */
public class ScreenshotCaptureListener implements IInvokedMethodListener {

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod() || testResult.getStatus() != ITestResult.FAILURE) {
            return;
        }
        ScreenshotMode mode = ScreenshotMode.fromString(
                ConfigManager.getString(ConfigKeys.SCREENSHOT_MODE, "FAILURE"));
        if (mode == ScreenshotMode.DISABLED) {
            return;
        }
        Path screenshot = null;
        if (WebDriverManager.isDriverActive()) {
            screenshot = ScreenshotUtils.capture(WebDriverManager.getDriver(), testResult.getName());
        } else if (MobileDriverManager.isDriverActive()) {
            screenshot = ScreenshotUtils.capture(MobileDriverManager.getDriver(), testResult.getName());
        }
        ReportManager.attachScreenshot(screenshot, testResult.getName());
    }
}
