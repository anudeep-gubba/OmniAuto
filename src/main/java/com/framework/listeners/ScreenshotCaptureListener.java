package com.framework.listeners;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.MobileDriverManager;
import com.framework.driver.WebDriverManager;
import com.framework.enums.ScreenshotMode;
import com.framework.reporting.AllureManager;
import com.framework.reporting.ReportManager;
import com.framework.secrets.SensitiveDataMasker;
import com.framework.utils.ScreenshotUtils;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
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
 *
 * <p><b>The same correctly-ordered "driver is still alive" window is reused for every other
 * failure-time diagnostic a real driver can answer</b> - page source, current URL, browser/
 * version (Web), device name/platform version/current activity (Mobile) - rather than adding
 * yet another listener that would need to re-derive this exact ordering guarantee itself. All
 * of it goes to Allure only ({@link AllureManager}, already no-op when Allure is disabled) -
 * Extent already gets the equivalent business narrative for free via the Logback bridge, and a
 * screenshot/page-source dump is exactly the kind of large, low-signal-until-you-need-it detail
 * this project's own Extent-vs-Allure split (see README's Reporting section) puts on the Allure
 * side deliberately.</p>
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
            WebDriver driver = WebDriverManager.getDriver();
            screenshot = ScreenshotUtils.capture(driver, testResult.getName());
            attachWebFailureDetail(driver);
        } else if (MobileDriverManager.isDriverActive()) {
            AppiumDriver driver = MobileDriverManager.getDriver();
            screenshot = ScreenshotUtils.capture(driver, testResult.getName());
            attachMobileFailureDetail(driver);
        }
        ReportManager.attachScreenshot(screenshot, testResult.getName());
    }

    private static void attachWebFailureDetail(WebDriver driver) {
        AllureManager.attachParameter("Current URL", SensitiveDataMasker.mask(driver.getCurrentUrl()));
        if (driver instanceof HasCapabilities hasCapabilities) {
            var capabilities = hasCapabilities.getCapabilities();
            AllureManager.attachParameter("Browser", String.valueOf(capabilities.getBrowserName()));
            AllureManager.attachParameter("Browser Version", String.valueOf(capabilities.getBrowserVersion()));
        }
        AllureManager.attachText("Page Source", SensitiveDataMasker.mask(driver.getPageSource()));
    }

    private static void attachMobileFailureDetail(AppiumDriver driver) {
        // AppiumDriver implements HasCapabilities unconditionally (unlike the plain WebDriver
        // interface in attachWebFailureDetail, which doesn't) - no instanceof check needed here.
        var capabilities = driver.getCapabilities();
        AllureManager.attachParameter("Device Name", String.valueOf(capabilities.getCapability("deviceName")));
        AllureManager.attachParameter("Platform", String.valueOf(capabilities.getPlatformName()));
        AllureManager.attachParameter("Platform Version", String.valueOf(capabilities.getCapability("platformVersion")));
        // currentActivity() is Android-only (AndroidDriver implements StartsActivity); iOS has
        // no direct equivalent, so this is best-effort and silently skipped there rather than
        // forced.
        if (driver instanceof AndroidDriver androidDriver) {
            try {
                AllureManager.attachParameter("Current Activity", androidDriver.currentActivity());
            } catch (RuntimeException ignored) {
                // Best-effort - some drivers/app states don't support this query; not worth
                // failing an otherwise-complete failure-diagnostics attachment over.
            }
        }
        AllureManager.attachText("Page Source", SensitiveDataMasker.mask(driver.getPageSource()));
    }
}
