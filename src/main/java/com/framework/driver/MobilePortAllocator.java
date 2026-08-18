package com.framework.driver;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hands out a fresh, globally-unique {@code systemPort} (Android/UiAutomator2) and
 * {@code wdaLocalPort} (iOS/XCUITest) on every call, so concurrent local mobile sessions -
 * multiple emulators, multiple physical devices, or a mix - never collide on the same port,
 * the standard, documented cause of "port already in use"/session-creation failures when
 * running Appium tests in parallel on one machine (requirement.md &sect;20).
 *
 * <p>Not used for {@link com.framework.enums.MobileDeviceProvider#BROWSERSTACK}: BrowserStack
 * allocates and isolates devices server-side, so there is no local port to coordinate.</p>
 *
 * <p><b>Found in practice, not assumed:</b> the first version cached one port per thread (a
 * {@code ThreadLocal}, reused across that thread's later mobile drivers) on the reasoning
 * that a thread reusing its own port sequentially is safe. A live run against the real
 * Android emulator immediately proved that wrong: a session that failed to fully start (a
 * real, pre-existing flakiness on this emulator/app combo - see
 * {@code MobileUtils.dismissSystemDialogsIfPresent}'s Javadoc) can leave the device-side
 * UiAutomator2 server still bound to that port; {@link com.framework.listeners.RetryAnalyzer}
 * then retried the exact same method on the exact same thread, which - under the cached
 * design - reused the identical port and collided with the still-bound leftover, failing with
 * "local port #8200 is busy" instead of recovering. Handing out a brand-new, never-repeated
 * port on every single call (not just once per thread) means a retry can never collide with
 * whatever state its own immediately-preceding failed attempt left behind, at the trivial
 * cost of port numbers climbing over a long run - the available range is enormous.</p>
 *
 * <p><b>Thread-safety classification (requirement.md &sect;21):</b> <b>thread-safe singleton</b>
 * (category 2) - both counters are {@link AtomicInteger}s, only ever read via
 * {@code getAndIncrement()}; no thread-local state at all. Starting each counter at Appium's
 * own documented default ({@code systemPort} 8200, {@code wdaLocalPort} 8100) means the very
 * first mobile driver of a run gets exactly the default port, same as before this existed.</p>
 */
final class MobilePortAllocator {

    private static final int DEFAULT_SYSTEM_PORT = 8200;
    private static final int DEFAULT_WDA_LOCAL_PORT = 8100;

    private static final AtomicInteger SYSTEM_PORT_COUNTER = new AtomicInteger(DEFAULT_SYSTEM_PORT);
    private static final AtomicInteger WDA_LOCAL_PORT_COUNTER = new AtomicInteger(DEFAULT_WDA_LOCAL_PORT);

    private MobilePortAllocator() {
    }

    static int nextSystemPort() {
        return SYSTEM_PORT_COUNTER.getAndIncrement();
    }

    static int nextWdaLocalPort() {
        return WDA_LOCAL_PORT_COUNTER.getAndIncrement();
    }
}
