package com.tests.tests.mobile;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.MobileDeviceMatrix;
import com.framework.driver.MobileDeviceMatrix.Row;
import com.framework.driver.MobileDriverManager;
import com.tests.application.base.BaseMobileTest;
import io.appium.java_client.AppiumDriver;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.framework.utils.Verify.assertNotNull;

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
 * one shared file, not duplicated per environment. Deliberately kept <b>pure per-platform</b>,
 * not mixed - {@code "android"} and {@code "ios"} each launch the same app concurrently across
 * every device of that one platform only, mirroring how a sequential/pooled mobile run is
 * always scoped to a single platform too (see {@code -Dmobile.platform}):</p>
 * <ul>
 *     <li>{@code "android"} - every device in {@code androidList} at once (one today,
 *     {@code Pixel_6a} - add a second id to {@code config/mobile-devices.json}'s
 *     {@code "android"} matrix for genuine multi-device Android parallelism, the same way
 *     {@code "ios"} already lists two).</li>
 *     <li>{@code "ios"} - two real, simultaneously-booted iOS simulators ("iPhone 17 Pro" and
 *     "iPhone 17") at once. Each gets its own {@code wdaLocalPort} from
 *     {@code MobilePortAllocator} (fresh on every call, never reused within a run), so two
 *     XCUITest sessions on this one machine don't collide. Confirmed genuinely concurrent (not
 *     just "used two threads sequentially") by reading Appium's own server log directly - the
 *     two {@code POST /session} requests arrived back-to-back with zero response logged in
 *     between.</li>
 * </ul>
 *
 * <p>Each row overrides {@code mobile.platform}/{@code device.name}/{@code platform.version}/
 * {@code app.path} via a thread-local {@link ConfigManager#setOverride} - deliberately per-row
 * here, unlike Web's environment-level {@code -Dbrowser=...}, because these overrides pick a
 * specific matrix row rather than a single run-wide value, and each row's own thread needs its
 * own value dispatched concurrently.</p>
 *
 * <p>An explicit {@code -Dmobile.platform=ios} (or {@code android}) skips the <em>other</em>
 * platform's matrix method entirely (see {@link #skipUnlessRequested}) - both methods still
 * carry the same plain {@code "mobile"} group (no separate {@code "android"}/{@code "ios"}
 * group exists to filter by), so without this a genuinely platform-scoped run (e.g. {@code
 * -Dgroups=mobile -Dmobile.platform=ios -Dparallel=methods -DthreadCount=2}) would still reach
 * {@code appLaunchesOnEachAndroidDeviceConcurrently} and fail it for want of a device nobody
 * asked this run to use.</p>
 */
public class MultiDeviceParallelTest extends BaseMobileTest {

    @DataProvider(name = "androidDeviceMatrix", parallel = true)
    public Object[][] androidDeviceMatrix() {
        return MobileDeviceMatrix.dataProvider("android");
    }

    @Test(groups = "mobile", dataProvider = "androidDeviceMatrix")
    public void appLaunchesOnEachAndroidDeviceConcurrently(Row device) {
        skipUnlessRequested("android");
        launchOnDevice(device);
    }

    @DataProvider(name = "iosDeviceMatrix", parallel = true)
    public Object[][] iosDeviceMatrix() {
        return MobileDeviceMatrix.dataProvider("ios");
    }

    @Test(groups = "mobile", dataProvider = "iosDeviceMatrix")
    public void appLaunchesOnEachIosSimulatorConcurrently(Row device) {
        skipUnlessRequested("ios");
        launchOnDevice(device);
    }

    /**
     * Skips this matrix method outright when {@code -Dmobile.platform} was given explicitly on
     * this command line and names the <em>other</em> platform - the same narrowing {@link
     * com.framework.driver.MobileDevicePool} already applies to the pooled tests (see its own
     * javadoc), extended here so e.g. {@code -Dmobile.platform=ios -Dgroups=mobile -Dparallel=...}
     * genuinely never touches Android at all, rather than failing {@code
     * appLaunchesOnEachAndroidDeviceConcurrently} for want of a device nobody asked this run to
     * use. A {@code SkipException}, not a failure - the whole run still reports success when
     * everything actually requested passed. Reads {@code System.getProperty} directly, not
     * {@link ConfigManager} - a plain {@code config/{env}.properties} default (present in every
     * env file already) must not skip this the same way an explicit {@code -D} does, or a run
     * with no platform filter at all would silently stop covering one platform's matrix.
     */
    private static void skipUnlessRequested(String thisMatrixPlatform) {
        String explicit = System.getProperty(ConfigKeys.MOBILE_PLATFORM);
        if (explicit != null && !explicit.trim().equalsIgnoreCase(thisMatrixPlatform)) {
            throw new SkipException("Skipped: -Dmobile.platform=" + explicit
                    + " excludes the '" + thisMatrixPlatform + "' device matrix.");
        }
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
