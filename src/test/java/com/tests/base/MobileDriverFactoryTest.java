package com.tests.base;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.driver.MobileDriverManager;
import com.framework.exceptions.ConfigurationException;
import com.framework.exceptions.DriverInitializationException;
import com.framework.secrets.SecretManager;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Phase 4/6 validation for the mobile driver path. Since Phase 6, a real
 * local Android emulator and app (apps/eventhub-app-release.apk) are available and every
 * {@code config/{env}.properties} defines working defaults for them (Phase 16 -
 * mobile is no longer dev-only; see {@code com.tests.mobile.*} for the actual
 * device/app validation this enables) - so the negative tests below force each
 * config key back to blank via a tier-5 override before asserting fail-fast,
 * since a bare "don't set it" no longer holds given every environment file now
 * provides real defaults for a genuine local setup.
 */
public class MobileDriverFactoryTest {

    @AfterMethod(alwaysRun = true)
    public void cleanup() {
        ConfigManager.clearThreadState();
    }

    @Test(groups = "smoke")
    public void missingMobilePlatformFailsFastWithoutNetworkAccess() {
        // A blank mobile.platform alone no longer fails fast - DriverFactory now falls back to
        // config/mobile-devices.json's androidList/iosList (defaulting to "android") instead of
        // requiring the key directly. Naming mobile.device.name explicitly skips that
        // resolution entirely (see DriverFactory.resolveActiveDeviceFromPoolIfNeeded), which is
        // the one remaining path where a blank platform is still a real, unrecoverable error.
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, "some-device");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, "");

        ConfigurationException exception = expectThrows(ConfigurationException.class, MobileDriverManager::getDriver);
        assertTrue(exception.getMessage().contains(ConfigKeys.MOBILE_PLATFORM));
        assertFalse(MobileDriverManager.isDriverActive());
    }

    @Test(groups = "smoke")
    public void missingAndroidAppIdentificationFailsFastWithoutNetworkAccess() {
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, "android");
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, "emulator-5554");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM_VERSION, "13");
        ConfigManager.setOverride(ConfigKeys.APPIUM_SERVER_URL, "http://127.0.0.1:4723");
        // Deliberately blank app path / package+activity, overriding dev.properties' real default.
        ConfigManager.setOverride(ConfigKeys.MOBILE_APP_PATH, "");

        DriverInitializationException exception =
                expectThrows(DriverInitializationException.class, MobileDriverManager::getDriver);
        assertTrue(exception.getMessage().contains(ConfigKeys.MOBILE_APP_PACKAGE));
    }

    @Test(groups = "smoke")
    public void missingIosAppIdentificationFailsFastWithoutNetworkAccess() {
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, "ios");
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, "iPhone 15");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM_VERSION, "17.0");
        ConfigManager.setOverride(ConfigKeys.APPIUM_SERVER_URL, "http://127.0.0.1:4723");
        // Deliberately blank app path / bundle id, overriding dev.properties' real default.
        ConfigManager.setOverride(ConfigKeys.MOBILE_APP_PATH, "");

        DriverInitializationException exception =
                expectThrows(DriverInitializationException.class, MobileDriverManager::getDriver);
        assertTrue(exception.getMessage().contains(ConfigKeys.MOBILE_BUNDLE_ID));
    }

    @Test(groups = "smoke")
    public void fullyConfiguredAndroidDriverGetsPastValidationAndAttemptsNetworkConnection() {
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, "android");
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, "emulator-5554");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM_VERSION, "13");
        ConfigManager.setOverride(ConfigKeys.APPIUM_SERVER_URL, "http://127.0.0.1:4723");
        ConfigManager.setOverride(ConfigKeys.MOBILE_APP_PACKAGE, "com.example.app");
        ConfigManager.setOverride(ConfigKeys.MOBILE_APP_ACTIVITY, ".MainActivity");

        // No Appium server is listening on 127.0.0.1:4723 here, so this must fail - but with a
        // Selenium/Appium connection-level exception, not one of our own config/validation types.
        // That distinction is the actual proof: capability building succeeded.
        Exception exception = expectThrows(Exception.class, MobileDriverManager::getDriver);
        assertFalse(exception instanceof ConfigurationException);
        assertFalse(exception instanceof DriverInitializationException);
    }

    /**
     * Phase 15: {@code mobile.device.provider=LOCAL} covers both an emulator and a real
     * physical device already, via the same {@code mobile.udid} config key {@code
     * MobileDriverFactoryTest} elsewhere already relies on for {@code mobile.device.name} -
     * no separate test needed for "physical device" specifically, since nothing in
     * DriverFactory branches on it.
     */
    @Test(groups = "smoke")
    public void missingBrowserStackAppIdFailsFastWithoutNetworkAccess() {
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_PROVIDER, "browserstack");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, "android");
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, "Samsung Galaxy S23");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM_VERSION, "13.0");
        // Deliberately no browserstack.app.id - checked before any credential lookup, so this
        // fails fast even without real BrowserStack secrets configured.
        DriverInitializationException exception =
                expectThrows(DriverInitializationException.class, MobileDriverManager::getDriver);
        assertTrue(exception.getMessage().contains(ConfigKeys.BROWSERSTACK_APP_ID));
    }

    /**
     * Live-validated only when real BrowserStack credentials are actually configured (this
     * repo's own {@code .secret.env} does not have any - see {@code .secret.env.example}) -
     * skips rather than faking credentials, consistent with this project's standing
     * preference for real validation over assumed-to-work code (see the
     * prefers-real-live-validation project note). When it does run, BrowserStack's real,
     * public hub endpoint is reachable regardless of credential validity, so a genuine
     * network-level response (an HTTP auth rejection, not a Java-level config exception)
     * still proves the capability map (including the nested {@code bstack:options} block)
     * was accepted as well-formed by a real external service.
     */
    @Test(groups = "smoke")
    public void fullyConfiguredBrowserStackDriverReachesTheRealBrowserStackHub() {
        if (!SecretManager.has("BROWSERSTACK_USERNAME")) {
            throw new SkipException("BROWSERSTACK_USERNAME not configured - see .secret.env.example.");
        }
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_PROVIDER, "browserstack");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, "android");
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, "Samsung Galaxy S23");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM_VERSION, "13.0");
        ConfigManager.setOverride(ConfigKeys.BROWSERSTACK_APP_ID, "bs://placeholder-app-id-for-this-test");

        Exception exception = expectThrows(Exception.class, MobileDriverManager::getDriver);
        assertFalse(exception instanceof ConfigurationException);
        assertFalse(exception instanceof DriverInitializationException);
    }

    /**
     * Proves {@link com.framework.driver.MobilePortAllocator}'s port assignment (a fresh,
     * never-repeated port per call - see its own Javadoc for why not cached per thread) does
     * not corrupt concurrent capability-building - real concurrent invocations (the same
     * TestNG primitive {@code parallel="methods"} uses), each independently reaching the same
     * genuine connection-level failure {@link #fullyConfiguredAndroidDriverGetsPastValidationAndAttemptsNetworkConnection()}
     * does, with no shared-state exception (e.g. two threads racing on the same port
     * assignment) instead.
     */
    @Test(groups = "smoke", invocationCount = 8, threadPoolSize = 4)
    public void concurrentLocalMobileCapabilityBuildingDoesNotInterfereAcrossThreads() {
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM, "android");
        ConfigManager.setOverride(ConfigKeys.MOBILE_DEVICE_NAME, "emulator-5554");
        ConfigManager.setOverride(ConfigKeys.MOBILE_PLATFORM_VERSION, "13");
        ConfigManager.setOverride(ConfigKeys.APPIUM_SERVER_URL, "http://127.0.0.1:4723");
        ConfigManager.setOverride(ConfigKeys.MOBILE_APP_PACKAGE, "com.example.app");
        ConfigManager.setOverride(ConfigKeys.MOBILE_APP_ACTIVITY, ".MainActivity");

        Exception exception = expectThrows(Exception.class, MobileDriverManager::getDriver);
        assertFalse(exception instanceof ConfigurationException);
        assertFalse(exception instanceof DriverInitializationException);
    }
}
