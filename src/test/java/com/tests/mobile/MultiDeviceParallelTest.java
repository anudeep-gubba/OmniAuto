package com.tests.mobile;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.MobileDriverManager;
import com.framework.testdata.TestDataManager;
import io.appium.java_client.AppiumDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

/**
 * Requirement.md &sect;20 (parallel execution) applied to mobile specifically: a device
 * matrix, each row launched on its own thread via TestNG's own
 * {@code @DataProvider(parallel = true)} - the mobile equivalent of how
 * {@code com.tests.web.LoginTest.loginWorksAcrossMultipleBrowsers} already covers multiple
 * browsers (there, sequential rows on one thread; here, genuinely concurrent rows on
 * different threads - closer to {@code parallel-multibrowser-suite.xml}'s old job, but data-
 * driven instead of suite-XML-driven, consistent with this project's "no suite XML, CLI
 * only" preference).
 *
 * <p>Two matrices, both live-verified, proving parallel mobile works both across platforms
 * and within one platform:</p>
 * <ul>
 *     <li>{@code mobile-devices.json} - cross-platform: a real Android emulator (Pixel_10)
 *     and a real iOS simulator ("iPhone 17 Pro") launching the same app concurrently.
 *     Confirmed genuinely concurrent (not just "used two threads sequentially") by reading
 *     Appium's own server log directly - the two {@code POST /session} requests arrived
 *     back-to-back with zero response logged in between.</li>
 *     <li>{@code mobile-devices-ios.json} - same-platform: two real, simultaneously-booted
 *     iOS simulators ("iPhone 17 Pro" and "iPhone 17") at once. Each gets its own
 *     {@code wdaLocalPort} from {@code MobilePortAllocator} (fresh on every call, never
 *     reused within a run), so two XCUITest sessions on this one machine don't collide.</li>
 * </ul>
 *
 * <p>Each row overrides {@code mobile.platform}/{@code device.name}/{@code platform.version}/
 * {@code app.path} via a thread-local {@link ConfigManager#setOverride} - the same mechanism
 * {@code LoginTest.loginWorksAcrossMultipleBrowsers} already uses for {@code browser}, just
 * dispatched concurrently instead of sequentially.</p>
 */
public class MultiDeviceParallelTest {

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ConfigManager.clearThreadState();
    }

    @DataProvider(name = "deviceMatrix", parallel = true)
    public Object[][] deviceMatrix() {
        return TestDataManager.load("mobile-devices.json").dataProvider(MobileDeviceRow.class);
    }

    @Test(groups = "mobile", dataProvider = "deviceMatrix")
    public void appLaunchesOnEachDeviceInTheMatrixConcurrently(MobileDeviceRow device) {
        launchOnDevice(device);
    }

    @DataProvider(name = "iosDeviceMatrix", parallel = true)
    public Object[][] iosDeviceMatrix() {
        return TestDataManager.load("mobile-devices-ios.json").dataProvider(MobileDeviceRow.class);
    }

    @Test(groups = "mobile", dataProvider = "iosDeviceMatrix")
    public void appLaunchesOnEachIosSimulatorConcurrently(MobileDeviceRow device) {
        launchOnDevice(device);
    }

    private static void launchOnDevice(MobileDeviceRow device) {
        // Deterministic regardless of any -Dmobile.device.provider passed at invocation time -
        // this test is specifically about LOCAL parallel devices, not the provider question.
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_PROVIDER, "LOCAL");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, device.platform());
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, device.deviceName());
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM_VERSION, device.platformVersion());
        ConfigManager.setOverride(ConfigKeys.MOBILE_APP_PATH, device.appPath());

        AppiumDriver driver = MobileDriverManager.getDriver();
        assertNotNull(driver.getSessionId(), "A real Appium session should exist for " + device.deviceName());
    }

    /** One row of a device-matrix JSON file - {@code name} is its JSON object key. */
    public record MobileDeviceRow(String name, String platform, String deviceName, String platformVersion, String appPath) {
    }
}
