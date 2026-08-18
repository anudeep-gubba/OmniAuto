package com.framework.driver;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.enums.BrowserType;
import com.framework.enums.MobileDeviceProvider;
import com.framework.enums.MobilePlatformType;
import com.framework.exceptions.DriverInitializationException;
import com.framework.secrets.SecretManager;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import io.appium.java_client.remote.options.BaseOptions;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds new {@link WebDriver} and {@link AppiumDriver} instances from
 * {@link ConfigManager} values. Stateless: every call creates a brand-new
 * driver instance. Thread-local storage and reuse is
 * {@link DriverManager}'s job, not this class's.
 *
 * <p>Package-private: {@link WebDriverManager} and {@link MobileDriverManager}
 * are the public entry points test/page-object code should use.</p>
 */
final class DriverFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DriverFactory.class);

    private DriverFactory() {
    }

    // ------------------------------------------------------------------
    // Web
    // ------------------------------------------------------------------

    static WebDriver createWebDriver() {
        BrowserType browserType = BrowserType.fromString(ConfigManager.getBrowser());
        boolean headless = ConfigManager.isHeadless();
        String resolution = ConfigManager.getResolution();

        WebDriver driver = switch (browserType) {
            case CHROME -> createChromeDriver(headless);
            case FIREFOX -> createFirefoxDriver(headless);
            case EDGE -> createEdgeDriver(headless);
            case SAFARI -> createSafariDriver(headless);
        };

        try {
            applyTimeouts(driver);
            applyResolution(driver, resolution);
        } catch (RuntimeException e) {
            // The browser process is already running at this point; without this, a failure
            // here (e.g. an invalid resolution) would leak it - it was never stored in
            // DriverManager, so DriverCleanupListener would never see it to quit it.
            LOGGER.warn("Quitting WebDriver after post-creation setup failed: {}", e.getMessage());
            driver.quit();
            throw e;
        }

        LOGGER.info("WebDriver created: browser={}, headless={}, resolution={}", browserType, headless, resolution);
        return driver;
    }

    private static WebDriver createChromeDriver(boolean headless) {
        ChromeOptions options = new ChromeOptions();
        // Works around a CDP-handshake issue seen with recent Chrome versions in headless CI.
        options.addArguments("--remote-allow-origins=*");
        if (headless) {
            options.addArguments("--headless=new");
        }
        return new ChromeDriver(options);
    }

    private static WebDriver createFirefoxDriver(boolean headless) {
        FirefoxOptions options = new FirefoxOptions();
        if (headless) {
            options.addArguments("-headless");
        }
        return new FirefoxDriver(options);
    }

    private static WebDriver createEdgeDriver(boolean headless) {
        EdgeOptions options = new EdgeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        return new EdgeDriver(options);
    }

    private static WebDriver createSafariDriver(boolean headless) {
        if (headless) {
            LOGGER.warn("Safari has no headless mode; continuing in headed mode "
                    + "(requires 'safaridriver --enable' and Safari > Develop > Allow Remote Automation).");
        }
        return new SafariDriver();
    }

    private static void applyTimeouts(WebDriver driver) {
        Duration pageLoad = Duration.ofSeconds(ConfigManager.getInt(ConfigKeys.PAGE_LOAD_TIMEOUT, 30));
        Duration script = Duration.ofSeconds(ConfigManager.getInt(ConfigKeys.SCRIPT_TIMEOUT, 30));
        Duration implicitWait = Duration.ofSeconds(ConfigManager.getInt(ConfigKeys.IMPLICIT_WAIT_TIMEOUT, 0));
        driver.manage().timeouts()
                .pageLoadTimeout(pageLoad)
                .scriptTimeout(script)
                .implicitlyWait(implicitWait);
    }

    private static void applyResolution(WebDriver driver, String resolution) {
        if ("maximize".equalsIgnoreCase(resolution)) {
            driver.manage().window().maximize();
            return;
        }
        String[] parts = resolution.toLowerCase().split("x");
        if (parts.length != 2) {
            throw new DriverInitializationException(
                    "Invalid resolution '" + resolution + "'. Expected '<width>x<height>' (e.g. 1920x1080) or 'maximize'.");
        }
        try {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            driver.manage().window().setSize(new Dimension(width, height));
        } catch (NumberFormatException e) {
            throw new DriverInitializationException(
                    "Invalid resolution '" + resolution + "'. Expected '<width>x<height>' (e.g. 1920x1080) or 'maximize'.", e);
        }
    }

    // ------------------------------------------------------------------
    // Mobile
    // ------------------------------------------------------------------

    static AppiumDriver createMobileDriver() {
        resolveActiveDeviceFromPoolIfNeeded();
        try {
            MobileDeviceProvider provider = MobileDeviceProvider.fromString(
                    ConfigManager.getString(ConfigKeys.MOBILE_DEVICE_PROVIDER, "LOCAL"));
            MobilePlatformType platform =
                    MobilePlatformType.fromString(ConfigManager.getString(ConfigKeys.MOBILE_PLATFORM));
            URL serverUrl = toUrl(serverUrlFor(provider));
            Duration commandTimeout = Duration.ofSeconds(ConfigManager.getInt(ConfigKeys.APPIUM_COMMAND_TIMEOUT, 60));

            AppiumDriver driver = switch (platform) {
                case ANDROID -> new AndroidDriver(serverUrl, buildAndroidOptions(provider, commandTimeout));
                case IOS -> new IOSDriver(serverUrl, buildIosOptions(provider, commandTimeout));
            };

            LOGGER.info("Mobile driver created: provider={}, platform={}, deviceName={}",
                    provider, platform, ConfigManager.getString(ConfigKeys.MOBILE_DEVICE_NAME));
            return driver;
        } catch (RuntimeException e) {
            // A device checked out of MobileDevicePool (a -Dparallel run), and any port(s)
            // checked out of MobilePortAllocator, are only ever released when a driver that
            // was actually created later gets quit - if creation itself fails (a real
            // SessionNotCreated, a bad capability, ...), nothing would otherwise release them,
            // permanently shrinking both pools for the rest of the run. A no-op release when
            // nothing was checked out (the ordinary, non-pooled case).
            MobileDevicePool.releaseForCurrentThread();
            MobilePortAllocator.releaseAllForCurrentThread();
            throw e;
        }
    }

    /**
     * A mobile run names no device *details* of its own on the command line - device name,
     * platform version, and app path all come from {@code config/mobile-devices.json} only
     * ({@link MobileDeviceMatrix}). Which device(s) is decided by whether {@code -Dparallel}
     * is present:
     * <ul>
     *     <li><b>No {@code -Dparallel}:</b> {@link ConfigKeys#MOBILE_PLATFORM} (android/ios,
     *     from {@code config/{env}.properties}) picks {@code androidList} or {@code iosList};
     *     every test then uses that list's first id, sequentially.</li>
     *     <li><b>{@code -Dparallel} present:</b> each test checks a device out of
     *     {@link MobileDevicePool} (both lists combined) and blocks if every device is
     *     currently busy - tests are distributed across the pool as a work queue, not one
     *     device per test regardless of load.</li>
     * </ul>
     * An explicit {@code mobile.device.name} (config, {@code -D}, or a test override - e.g.
     * {@code MultiDeviceParallelTest} setting one per matrix row) always wins over both and
     * skips this resolution entirely.
     */
    private static void resolveActiveDeviceFromPoolIfNeeded() {
        if (ConfigManager.getString(ConfigKeys.MOBILE_DEVICE_NAME, null) != null) {
            return;
        }
        MobileDeviceMatrix.Row device = MobileDevicePool.isPooledRunActive()
                ? MobileDevicePool.checkout()
                : MobileDeviceMatrix.loadDevice(firstIdInActivePlatformList());
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, device.platform());
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, device.deviceName());
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM_VERSION, device.platformVersion());
        ConfigManager.setOverride(ConfigKeys.MOBILE_APP_PATH, device.appPath());
        ConfigManager.setOverride(ConfigKeys.MOBILE_HYBRID, String.valueOf(device.hybrid()));
        if (device.appiumServerUrl() != null) {
            // Only this specific device names its own Appium server (a real multi-machine
            // device lab) - every other device keeps using the shared appium.server.url.
            ConfigManager.setOverride(ConfigKeys.APPIUM_SERVER_URL, device.appiumServerUrl());
        }
    }

    /** The first device id in {@code mobile-devices.json}'s {@code androidList}/{@code iosList}, per {@link ConfigKeys#MOBILE_PLATFORM}. */
    private static String firstIdInActivePlatformList() {
        String platform = ConfigManager.getString(ConfigKeys.MOBILE_PLATFORM, "android").trim().toLowerCase(Locale.ROOT);
        List<String> ids = "ios".equals(platform) ? MobileDeviceMatrix.iosList() : MobileDeviceMatrix.androidList();
        if (ids.isEmpty()) {
            throw new DriverInitializationException(
                    "config/mobile-devices.json's '" + (platform.equals("ios") ? "iosList" : "androidList")
                            + "' is empty.");
        }
        return ids.get(0);
    }

    private static String serverUrlFor(MobileDeviceProvider provider) {
        return switch (provider) {
            case LOCAL -> ConfigManager.getString(ConfigKeys.APPIUM_SERVER_URL);
            // BrowserStack's own documented, stable hub endpoint - overridable (e.g. an
            // enterprise/private BrowserStack hub) but not required to be configured for the
            // common case.
            case BROWSERSTACK ->
                    ConfigManager.getString(ConfigKeys.BROWSERSTACK_SERVER_URL, "https://hub-cloud.browserstack.com/wd/hub");
        };
    }

    private static UiAutomator2Options buildAndroidOptions(MobileDeviceProvider provider, Duration commandTimeout) {
        UiAutomator2Options options = new UiAutomator2Options()
                .setAutomationName(ConfigManager.getString(ConfigKeys.MOBILE_AUTOMATION_NAME, "UiAutomator2"))
                .setNewCommandTimeout(commandTimeout);

        if (provider == MobileDeviceProvider.BROWSERSTACK) {
            applyBrowserStackOptions(options);
            return options;
        }

        // LOCAL: an emulator or a physical device on this machine - same code path either
        // way (see MobileDeviceProvider's own Javadoc for why there is no separate value).
        String deviceName = ConfigManager.getString(ConfigKeys.MOBILE_DEVICE_NAME);
        options.setDeviceName(deviceName)
                .setPlatformVersion(ConfigManager.getString(ConfigKeys.MOBILE_PLATFORM_VERSION))
                .setSystemPort(MobilePortAllocator.checkoutSystemPort(deviceName));

        if (ConfigManager.getBoolean(ConfigKeys.MOBILE_HYBRID, false)) {
            // Only requested for a device whose config/mobile-devices.json entry sets
            // "hybrid": true (a WebView/Chrome-hybrid app) - most Android devices don't need
            // a chromedriverPort at all.
            options.setChromedriverPort(MobilePortAllocator.checkoutChromedriverPort(deviceName));
        }

        String udid = ConfigManager.getString(ConfigKeys.MOBILE_UDID, null);
        if (udid != null) {
            options.setUdid(udid);
        }

        String appPath = resolveAppPath(ConfigManager.getString(ConfigKeys.MOBILE_APP_PATH, null));
        String appPackage = ConfigManager.getString(ConfigKeys.MOBILE_APP_PACKAGE, null);
        String appActivity = ConfigManager.getString(ConfigKeys.MOBILE_APP_ACTIVITY, null);
        if (appPath != null) {
            options.setApp(appPath);
        } else if (appPackage != null && appActivity != null) {
            options.setAppPackage(appPackage).setAppActivity(appActivity);
        } else {
            throw new DriverInitializationException(
                    "Android mobile driver requires either '" + ConfigKeys.MOBILE_APP_PATH + "' or both '"
                            + ConfigKeys.MOBILE_APP_PACKAGE + "' and '" + ConfigKeys.MOBILE_APP_ACTIVITY
                            + "'. None were configured.");
        }

        // Many real apps show a splash activity that transitions to a main activity within
        // ~1s; Appium's default post-launch check waits for the exact manifest-declared
        // launch activity and can miss that transition entirely, failing session creation
        // even though the app launched fine (found against apps/swaglabs.apk - see Phase 6
        // summary). A same-package wildcard tolerates any activity the app lands on; when
        // only an app path is given (package not explicitly known), fall back to a fully
        // open wildcard rather than trying to introspect the APK just for this.
        String defaultWaitActivity = appPackage != null ? appPackage + ".*" : "*";
        options.setAppWaitActivity(ConfigManager.getString(ConfigKeys.MOBILE_APP_WAIT_ACTIVITY, defaultWaitActivity));
        return options;
    }

    private static XCUITestOptions buildIosOptions(MobileDeviceProvider provider, Duration commandTimeout) {
        XCUITestOptions options = new XCUITestOptions()
                .setAutomationName(ConfigManager.getString(ConfigKeys.MOBILE_AUTOMATION_NAME, "XCUITest"))
                .setNewCommandTimeout(commandTimeout);

        if (provider == MobileDeviceProvider.BROWSERSTACK) {
            applyBrowserStackOptions(options);
            return options;
        }

        // LOCAL: a simulator or a physical device on this machine - same code path either
        // way (see MobileDeviceProvider's own Javadoc for why there is no separate value).
        String deviceName = ConfigManager.getString(ConfigKeys.MOBILE_DEVICE_NAME);
        options.setDeviceName(deviceName)
                .setPlatformVersion(ConfigManager.getString(ConfigKeys.MOBILE_PLATFORM_VERSION))
                .setWdaLocalPort(MobilePortAllocator.checkoutWdaLocalPort(deviceName));

        String udid = ConfigManager.getString(ConfigKeys.MOBILE_UDID, null);
        if (udid != null) {
            options.setUdid(udid);
        }

        String appPath = resolveAppPath(ConfigManager.getString(ConfigKeys.MOBILE_APP_PATH, null));
        String bundleId = ConfigManager.getString(ConfigKeys.MOBILE_BUNDLE_ID, null);
        if (appPath != null) {
            options.setApp(appPath);
        } else if (bundleId != null) {
            options.setBundleId(bundleId);
        } else {
            throw new DriverInitializationException(
                    "iOS mobile driver requires either '" + ConfigKeys.MOBILE_APP_PATH + "' or '"
                            + ConfigKeys.MOBILE_BUNDLE_ID + "'. Neither was configured.");
        }
        return options;
    }

    /**
     * Shared across Android/iOS - both {@link UiAutomator2Options} and {@link XCUITestOptions}
     * extend {@link BaseOptions}, so the {@code app}/{@code bstack:options} capabilities
     * BrowserStack needs are identical either way; only the platform-specific options built
     * before this is called (automationName, newCommandTimeout) differ.
     *
     * <p>Deliberately does <em>not</em> call {@code setDeviceName}/{@code setPlatformVersion}
     * on {@code options} itself - BrowserStack reads device selection from
     * {@code bstack:options.deviceName}/{@code osVersion}, not the top-level
     * {@code appium:deviceName}/{@code platformVersion} capabilities LOCAL uses, so this
     * reuses the same {@link com.framework.constants.ConfigKeys#MOBILE_DEVICE_NAME}/
     * {@link com.framework.constants.ConfigKeys#MOBILE_PLATFORM_VERSION} config values (one
     * "which device" concept regardless of provider) but places them correctly for this
     * provider instead of guessing both locations would be harmlessly redundant.</p>
     */
    private static void applyBrowserStackOptions(BaseOptions<?> options) {
        String appId = ConfigManager.getString(ConfigKeys.BROWSERSTACK_APP_ID, null);
        if (appId == null) {
            throw new DriverInitializationException(
                    "BrowserStack mobile testing requires '" + ConfigKeys.BROWSERSTACK_APP_ID + "' - a 'bs://...' "
                            + "app ID returned by BrowserStack's app-upload API "
                            + "(https://www.browserstack.com/docs/app-automate/appium/upload-app). None was configured.");
        }
        options.setCapability("app", appId);

        Map<String, Object> bstackOptions = new LinkedHashMap<>();
        // Credentials are secrets (RULE 6) - resolved via SecretManager, never a plain
        // config value; SecretManager also registers them with SensitiveDataMasker
        // automatically, so they are masked wherever these capabilities get logged.
        bstackOptions.put("userName", SecretManager.get("BROWSERSTACK_USERNAME"));
        bstackOptions.put("accessKey", SecretManager.get("BROWSERSTACK_ACCESS_KEY"));
        bstackOptions.put("deviceName", ConfigManager.getString(ConfigKeys.MOBILE_DEVICE_NAME));
        bstackOptions.put("osVersion", ConfigManager.getString(ConfigKeys.MOBILE_PLATFORM_VERSION));
        putIfConfigured(bstackOptions, "projectName", ConfigKeys.BROWSERSTACK_PROJECT_NAME);
        putIfConfigured(bstackOptions, "buildName", ConfigKeys.BROWSERSTACK_BUILD_NAME);
        options.setCapability("bstack:options", bstackOptions);
    }

    private static void putIfConfigured(Map<String, Object> capabilities, String capabilityName, String configKey) {
        String value = ConfigManager.getString(configKey, null);
        if (value != null) {
            capabilities.put(capabilityName, value);
        }
    }

    private static URL toUrl(String rawUrl) {
        try {
            return URI.create(rawUrl).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new DriverInitializationException("Invalid Appium server URL '" + rawUrl + "'.", e);
        }
    }

    /**
     * Resolves a relative {@code mobile.app.path} (e.g. {@code apps/swaglabs.apk}) against
     * the current working directory, so config files can use paths portable across machines
     * instead of an absolute path baked in per developer. Already-absolute paths pass through
     * unchanged; {@code null}/blank passes through as {@code null} for the "no app path
     * configured" case (blank is treated the same as absent, consistent with how a tier-5
     * override forces a normally-defaulted key back to "not set" for testing).
     */
    private static String resolveAppPath(String appPath) {
        if (appPath == null || appPath.isBlank()) {
            return null;
        }
        return java.nio.file.Path.of(appPath).toAbsolutePath().toString();
    }
}
