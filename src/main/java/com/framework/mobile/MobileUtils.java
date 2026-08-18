package com.framework.mobile;

import com.framework.driver.MobileDriverManager;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.HidesKeyboard;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Device/app-level Mobile operations that are not tied to a specific element:
 * keyboard, app lifecycle, and screen-relative gestures. Mirrors
 * {@link com.framework.web.WebUtils}. Element-level interactions (tap, type,
 * scroll-to-element) live in {@link MobileActions} instead.
 *
 * <p>{@link com.framework.driver.DriverFactory} only ever creates
 * {@code AndroidDriver} or {@code IOSDriver}, both of which implement
 * {@link HidesKeyboard}/{@link InteractsWithApps} - the {@code instanceof}
 * checks here are defensive, not expected to ever fail in practice.</p>
 */
public final class MobileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobileUtils.class);
    private static final Duration SWIPE_DURATION = Duration.ofMillis(400);

    private MobileUtils() {
    }

    private static final int MAX_DIALOG_DISMISS_ATTEMPTS = 4;
    private static final long DIALOG_SETTLE_MILLIS = 1000;

    // ------------------------------------------------------------------
    // System dialogs
    // ------------------------------------------------------------------

    /**
     * Dismisses unexpected Android system dialogs (an ANR "isn't responding"
     * prompt, an OS/API compatibility warning, ...) that can appear on
     * resource-constrained emulators or when a legacy app runs on a much
     * newer OS - found in practice launching apps/swaglabs.apk (targets API
     * 28) on an Android 17 emulator; see Phase 6 summary. Best-effort: taps
     * the last button in each dialog (the conventional "OK"/"Wait"/dismiss
     * choice) up to {@value #MAX_DIALOG_DISMISS_ATTEMPTS} times, then stops
     * once no more button-bearing dialogs are found. No-op on iOS.
     *
     * <p>The brief pause after each dismissal is deliberate and not a RULE-8
     * violation: it is not standing in for an element wait (nothing here
     * blocks on a known, expected element) - it is settle time for a second,
     * unrelated system dialog that can render moments after the first closes,
     * which was found in practice to otherwise race past this method
     * entirely (see Phase 6 summary).</p>
     */
    public static void dismissSystemDialogsIfPresent() {
        if (!(MobileDriverManager.getDriver() instanceof AndroidDriver driver)) {
            return;
        }
        for (int attempt = 1; attempt <= MAX_DIALOG_DISMISS_ATTEMPTS; attempt++) {
            List<WebElement> buttons = driver.findElements(By.className("android.widget.Button"));
            if (buttons.isEmpty()) {
                return;
            }
            LOGGER.warn("Dismissing an unexpected system dialog (attempt {}/{}, {} button(s) found).",
                    attempt, MAX_DIALOG_DISMISS_ATTEMPTS, buttons.size());
            buttons.get(buttons.size() - 1).click();
            try {
                Thread.sleep(DIALOG_SETTLE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Keyboard
    // ------------------------------------------------------------------

    public static void hideKeyboard() {
        AppiumDriver driver = MobileDriverManager.getDriver();
        if (driver instanceof HidesKeyboard hidesKeyboard) {
            hidesKeyboard.hideKeyboard();
            LOGGER.info("Hid the on-screen keyboard.");
        } else {
            LOGGER.warn("Current driver does not support hiding the keyboard.");
        }
    }

    // ------------------------------------------------------------------
    // App lifecycle
    // ------------------------------------------------------------------

    public static void activateApp(String appId) {
        AppiumDriver driver = MobileDriverManager.getDriver();
        if (driver instanceof InteractsWithApps interactsWithApps) {
            interactsWithApps.activateApp(appId);
            LOGGER.info("Activated app: {}", appId);
        } else {
            LOGGER.warn("Current driver does not support app lifecycle management.");
        }
    }

    public static void terminateApp(String appId) {
        AppiumDriver driver = MobileDriverManager.getDriver();
        if (driver instanceof InteractsWithApps interactsWithApps) {
            interactsWithApps.terminateApp(appId);
            LOGGER.info("Terminated app: {}", appId);
        } else {
            LOGGER.warn("Current driver does not support app lifecycle management.");
        }
    }

    public static boolean isAppInstalled(String appId) {
        AppiumDriver driver = MobileDriverManager.getDriver();
        if (driver instanceof InteractsWithApps interactsWithApps) {
            return interactsWithApps.isAppInstalled(appId);
        }
        LOGGER.warn("Current driver does not support app lifecycle management.");
        return false;
    }

    // ------------------------------------------------------------------
    // Screen-relative gestures
    // ------------------------------------------------------------------

    public static void swipeUp() {
        Dimension size = MobileDriverManager.getDriver().manage().window().getSize();
        int centerX = size.getWidth() / 2;
        swipe(centerX, (int) (size.getHeight() * 0.8), centerX, (int) (size.getHeight() * 0.2));
        LOGGER.info("Swiped up.");
    }

    public static void swipeDown() {
        Dimension size = MobileDriverManager.getDriver().manage().window().getSize();
        int centerX = size.getWidth() / 2;
        swipe(centerX, (int) (size.getHeight() * 0.2), centerX, (int) (size.getHeight() * 0.8));
        LOGGER.info("Swiped down.");
    }

    private static void swipe(int startX, int startY, int endX, int endY) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), startX, startY))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerMove(SWIPE_DURATION, PointerInput.Origin.viewport(), endX, endY))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        MobileActions.perform(swipe);
    }
}
