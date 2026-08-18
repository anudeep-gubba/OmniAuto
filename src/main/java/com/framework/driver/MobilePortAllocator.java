package com.framework.driver;

import com.framework.exceptions.DriverInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Hands out a fresh, thread-safe, <b>bounded and reusable</b> {@code systemPort}
 * (Android/UiAutomator2), {@code wdaLocalPort} (iOS/XCUITest), or {@code chromedriverPort}
 * (Android WebView/hybrid content) on every driver creation, so concurrent local mobile
 * sessions - multiple emulators, multiple physical devices, or a mix - never collide on the
 * same port, the standard, documented cause of "port already in use"/session-creation
 * failures when running Appium tests in parallel on one machine (requirement.md &sect;20).
 *
 * <p>The shared Appium <em>server</em> port ({@code appium.server.url}, {@code 127.0.0.1:4723}
 * by default) is unrelated to any of this - one HTTP port legitimately serves many concurrent
 * sessions, same as any web server. These three port types are the per-session, device-side
 * automation ports each concurrent session needs its own of.</p>
 *
 * <p><b>Checkout, not just increment:</b> each port type is a bounded pool (a
 * {@link LinkedBlockingQueue} of every port in its configured range - see
 * {@link MobileDeviceMatrix#portRange}), not an ever-climbing counter. A driver creation
 * checks a port out ({@link #checkoutSystemPort}/{@link #checkoutWdaLocalPort}/
 * {@link #checkoutChromedriverPort}, blocking if the range is fully checked out); quitting
 * that driver - success, failure, or a retry - returns every port that thread is holding via
 * {@link #releaseAllForCurrentThread()}, so the pool never leaks or grows unbounded across a
 * long run. {@link DriverFactory} calls both: release on any exception during driver creation
 * (a checkout with no driver to eventually quit), and {@code MobileDriverManager.quitDriver()}
 * pairs a successful checkout with its release on normal completion.</p>
 *
 * <p><b>Never a fixed port per device, deliberately:</b> an earlier version cached one port
 * per thread (reused across that thread's later mobile drivers) on the reasoning that a
 * thread reusing its own port sequentially is safe. A live run against a real Android emulator
 * immediately proved that wrong: a session that failed to fully start can leave the
 * device-side UiAutomator2 server still bound to that port; {@link com.framework.listeners.RetryAnalyzer}
 * then retried the exact same method on the exact same thread, which - under the cached
 * design - reused the identical port and collided with the still-bound leftover, failing with
 * "local port #8200 is busy" instead of recovering. A released port always goes back to the
 * back of its pool's queue, and a fresh checkout never reuses the port a just-failed attempt
 * held, so a retry can't collide with its own preceding failure's leftover state this way.</p>
 *
 * <p><b>Thread-safety classification (requirement.md &sect;21):</b> <b>thread-safe singleton</b>
 * (category 2) - each pool is a {@link LinkedBlockingQueue}; which ports a thread currently
 * holds is tracked in a {@code ThreadLocal} map (category 3), so one thread's checkout is
 * never visible to another's. Ranges default to Appium's own documented defaults
 * ({@code systemPort} 8200, {@code wdaLocalPort} 8100, {@code chromedriverPort} 9515) and are
 * configurable per {@link PortType} via {@code config/mobile-devices.json}'s {@code ports}
 * section, with no code change.</p>
 */
final class MobilePortAllocator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobilePortAllocator.class);

    enum PortType {
        SYSTEM_PORT("systemPort", 8200, 50),
        WDA_LOCAL_PORT("wdaLocalPort", 8100, 50),
        CHROMEDRIVER_PORT("chromedriverPort", 9515, 50);

        final String jsonKey;
        final int defaultStart;
        final int defaultCount;

        PortType(String jsonKey, int defaultStart, int defaultCount) {
            this.jsonKey = jsonKey;
            this.defaultStart = defaultStart;
            this.defaultCount = defaultCount;
        }
    }

    private static final Map<PortType, BlockingQueue<Integer>> POOLS = new ConcurrentHashMap<>();

    private static final ThreadLocal<Map<PortType, Integer>> CHECKED_OUT_BY_THREAD =
            ThreadLocal.withInitial(() -> new EnumMap<>(PortType.class));

    private MobilePortAllocator() {
    }

    /** Checks out a fresh {@code systemPort} for {@code deviceLabel} (logged; e.g. a device name), blocking if none is free. */
    static int checkoutSystemPort(String deviceLabel) {
        return checkout(PortType.SYSTEM_PORT, deviceLabel);
    }

    /** Checks out a fresh {@code wdaLocalPort} for {@code deviceLabel} (logged), blocking if none is free. */
    static int checkoutWdaLocalPort(String deviceLabel) {
        return checkout(PortType.WDA_LOCAL_PORT, deviceLabel);
    }

    /** Checks out a fresh {@code chromedriverPort} for {@code deviceLabel} (logged), blocking if none is free. */
    static int checkoutChromedriverPort(String deviceLabel) {
        return checkout(PortType.CHROMEDRIVER_PORT, deviceLabel);
    }

    /** Returns every port type the calling thread currently holds to its pool. No-op if it holds none. */
    static void releaseAllForCurrentThread() {
        Map<PortType, Integer> held = CHECKED_OUT_BY_THREAD.get();
        if (held.isEmpty()) {
            return;
        }
        held.forEach((type, port) -> {
            pool(type).offer(port);
            LOGGER.info("Released {} {} (thread={})", type.jsonKey, port, Thread.currentThread().getName());
        });
        held.clear();
    }

    private static int checkout(PortType type, String deviceLabel) {
        try {
            int port = pool(type).take();
            CHECKED_OUT_BY_THREAD.get().put(type, port);
            LOGGER.info("Allocated {} {} for device '{}' (thread={})",
                    type.jsonKey, port, deviceLabel, Thread.currentThread().getName());
            return port;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DriverInitializationException("Interrupted while waiting for a free " + type.jsonKey + ".", e);
        }
    }

    private static BlockingQueue<Integer> pool(PortType type) {
        return POOLS.computeIfAbsent(type, MobilePortAllocator::buildPool);
    }

    private static BlockingQueue<Integer> buildPool(PortType type) {
        MobileDeviceMatrix.PortRange range =
                MobileDeviceMatrix.portRange(type.jsonKey, type.defaultStart, type.defaultCount);
        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(range.count());
        for (int port = range.start(); port < range.start() + range.count(); port++) {
            queue.offer(port);
        }
        LOGGER.info("Initialized {} pool: {} ports starting at {}", type.jsonKey, range.count(), range.start());
        return queue;
    }
}
