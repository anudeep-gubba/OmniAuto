package com.framework.utils;

import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import java.time.Duration;

/**
 * Builds a {@link FluentWait} using the framework's centralized timeout/polling
 * configuration ({@code explicit.wait.timeout}, {@code polling.interval}) -
 * never hardcoded, so a whole suite's wait behavior is one config change
 * (requirement.md &sect;5: "All waits must be centralized").
 *
 * <p>Generic over {@link SearchContext} so both {@code com.framework.web}
 * ({@code WebDriver}- and {@code WebElement}-scoped waits) and
 * {@code com.framework.mobile} ({@code AppiumDriver}-scoped waits) share one
 * implementation instead of each defining their own (RULE 5).</p>
 */
public final class WaitUtils {

    private WaitUtils() {
    }

    public static <T extends SearchContext> Wait<T> buildFluentWait(T searchContext) {
        Duration timeout = Duration.ofSeconds(ConfigManager.getInt(ConfigKeys.EXPLICIT_WAIT_TIMEOUT, 15));
        Duration polling = Duration.ofMillis(ConfigManager.getInt(ConfigKeys.POLLING_INTERVAL, 500));
        return new FluentWait<>(searchContext)
                .withTimeout(timeout)
                .pollingEvery(polling)
                .ignoring(NoSuchElementException.class);
    }
}
