package com.framework.driver;

import io.appium.java_client.AppiumDriver;

/**
 * Public entry point for Mobile (Appium) driver access. Lazily creates a
 * driver for the calling thread on first use, mirroring
 * {@link WebDriverManager}, and must be paired with {@link #quitDriver()} on
 * test completion (requirement.md &sect;33) &mdash; handled automatically by
 * {@link com.framework.listeners.DriverCleanupListener}.
 */
public final class MobileDriverManager {

    private MobileDriverManager() {
    }

    public static AppiumDriver getDriver() {
        AppiumDriver driver = DriverManager.getMobileDriverOrNull();
        if (driver == null) {
            driver = DriverFactory.createMobileDriver();
            DriverManager.setMobileDriver(driver);
        }
        return driver;
    }

    public static boolean isDriverActive() {
        return DriverManager.getMobileDriverOrNull() != null;
    }

    /**
     * Quits this thread's driver and releases whatever it was holding: the device, if this run
     * drew it from {@link MobileDevicePool} (a {@code -Dparallel} run), and any port(s)
     * checked out of {@link MobilePortAllocator} ({@code systemPort}/{@code wdaLocalPort}, plus
     * {@code chromedriverPort} for a hybrid app). Both releases are no-ops when nothing was
     * checked out that way (the ordinary, non-pooled single-device case still releases its
     * ports, just not a pooled device).
     */
    public static void quitDriver() {
        DriverManager.quitMobileDriver();
        MobileDevicePool.releaseForCurrentThread();
        MobilePortAllocator.releaseAllForCurrentThread();
    }
}
