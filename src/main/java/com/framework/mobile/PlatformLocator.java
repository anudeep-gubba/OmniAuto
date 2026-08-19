package com.framework.mobile;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.enums.MobilePlatformType;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * A {@link By} that resolves to one of two platform-specific locators at find-time, based on
 * the mobile driver's currently active platform ({@link ConfigKeys#MOBILE_PLATFORM}, the same
 * value {@link com.framework.driver.DriverFactory} used to pick Android vs. iOS when the driver
 * itself was created).
 *
 * <p>Only needed for the locators that genuinely differ per platform - an XPath anchored to a
 * platform-specific element class name ({@code XCUIElementType*} on iOS vs. {@code
 * android.view.View} on Android, since Flutter renders its own widgets rather than native ones
 * and exposes every one of them under that single class per platform). Locators built from
 * {@code AppiumBy.accessibilityId(...)} don't need this and are used as-is: one Flutter
 * Semantics tree drives both iOS's {@code accessibilityId} and Android's {@code content-desc}
 * through that same strategy - verified live on both platforms.</p>
 *
 * <p>Delegates to whichever {@link By} the active platform names, so a {@link PlatformLocator}
 * is a drop-in {@code By} anywhere one is already accepted - driver-scoped ({@link MobileWaits},
 * {@link MobileActions}) and component-root-scoped ({@link BaseMobileComponent#find}) alike.</p>
 */
public final class PlatformLocator extends By {

    private final By androidLocator;
    private final By iosLocator;

    private PlatformLocator(By androidLocator, By iosLocator) {
        this.androidLocator = androidLocator;
        this.iosLocator = iosLocator;
    }

    /** Builds a locator that resolves to {@code androidLocator} or {@code iosLocator} depending on the active platform. */
    public static By of(By androidLocator, By iosLocator) {
        return new PlatformLocator(androidLocator, iosLocator);
    }

    @Override
    public List<WebElement> findElements(SearchContext context) {
        return active().findElements(context);
    }

    @Override
    public WebElement findElement(SearchContext context) {
        return active().findElement(context);
    }

    private By active() {
        MobilePlatformType platform =
                MobilePlatformType.fromString(ConfigManager.getString(ConfigKeys.MOBILE_PLATFORM));
        return platform == MobilePlatformType.ANDROID ? androidLocator : iosLocator;
    }

    @Override
    public String toString() {
        return "PlatformLocator(android=" + androidLocator + ", ios=" + iosLocator + ")";
    }
}
