package com.tests.tests.mobile;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.MobileDeviceMatrix;
import com.framework.driver.MobileDeviceMatrix.Row;
import com.framework.driver.MobileDriverManager;
import com.tests.application.base.BaseMobileTest;
import io.appium.java_client.AppiumDriver;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

/**
 * Requirement.md &sect;20 (parallel execution) applied to mobile specifically: a device
 * matrix, each row launched on its own thread via TestNG's own
 * {@code @DataProvider(parallel = true)}. Web's own cross-browser coverage takes the opposite
 * shape deliberately - separate parallel CI jobs (see {@code com.tests.tests.web.LoginTest}'s
 * class javadoc), not an in-test device/browser loop - but Mobile's device matrix has no CI
 * runner to spread across (no emulator/Appium there), so this class is genuinely the one place
 * that coverage can live: rows dispatched concurrently, on different threads, in-process.
 *
 * <p>Both matrices live in {@code config/mobile-devices.json} - see {@link MobileDeviceMatrix} -
 * one shared file, not duplicated per environment. Two matrices, both live-verified, proving
 * parallel mobile works both across platforms and within one platform:</p>
 * <ul>
 *     <li>{@code "cross-platform"} - a real Android emulator (Pixel_10) and a real iOS
 *     simulator ("iPhone 17 Pro") launching the same app concurrently. Confirmed genuinely
 *     concurrent (not just "used two threads sequentially") by reading Appium's own server log
 *     directly - the two {@code POST /session} requests arrived back-to-back with zero
 *     response logged in between.</li>
 *     <li>{@code "ios"} - two real, simultaneously-booted iOS simulators ("iPhone 17 Pro" and
 *     "iPhone 17") at once. Each gets its own {@code wdaLocalPort} from
 *     {@code MobilePortAllocator} (fresh on every call, never reused within a run), so two
 *     XCUITest sessions on this one machine don't collide.</li>
 * </ul>
 *
 * <p>Each row overrides {@code mobile.platform}/{@code device.name}/{@code platform.version}/
 * {@code app.path} via a thread-local {@link ConfigManager#setOverride} - deliberately per-row
 * here, unlike Web's environment-level {@code -Dbrowser=...}, because these overrides pick a
 * specific matrix row rather than a single run-wide value, and each row's own thread needs its
 * own value dispatched concurrently.</p>
 */
public class MultiDeviceParallelTest extends BaseMobileTest {

    @DataProvider(name = "deviceMatrix", parallel = true)
    public Object[][] deviceMatrix() {
        return MobileDeviceMatrix.dataProvider("cross-platform");
    }

    @Test(groups = "mobile", dataProvider = "deviceMatrix")
    public void appLaunchesOnEachDeviceInTheMatrixConcurrently(Row device) {
        launchOnDevice(device);
    }

    @DataProvider(name = "iosDeviceMatrix", parallel = true)
    public Object[][] iosDeviceMatrix() {
        return MobileDeviceMatrix.dataProvider("ios");
    }

    @Test(groups = "mobile", dataProvider = "iosDeviceMatrix")
    public void appLaunchesOnEachIosSimulatorConcurrently(Row device) {
        launchOnDevice(device);
    }

    private static void launchOnDevice(Row device) {
        // Deterministic regardless of any -Dmobile.device.provider passed at invocation time -
        // this test is specifically about LOCAL parallel devices, not the provider question.
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_PROVIDER, "LOCAL");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, device.platform());
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, device.deviceName());
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM_VERSION, device.platformVersion());
        ConfigManager.setOverride(ConfigKeys.MOBILE_APP_PATH, device.appPath());
        ConfigManager.setOverride(ConfigKeys.MOBILE_HYBRID, String.valueOf(device.hybrid()));
        if (device.appiumServerUrl() != null) {
            ConfigManager.setOverride(ConfigKeys.APPIUM_SERVER_URL, device.appiumServerUrl());
        }

        AppiumDriver driver = MobileDriverManager.getDriver();
        assertNotNull(driver.getSessionId(), "A real Appium session should exist for " + device.deviceName());
    }
}
