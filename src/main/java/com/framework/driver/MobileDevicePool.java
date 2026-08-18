package com.framework.driver;

import com.framework.exceptions.ConfigurationException;
import com.framework.exceptions.DriverInitializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Distributes ordinary mobile {@code @Test} methods across a pool of real devices as a work
 * queue, active only when {@code -Dparallel} is present on the command line
 * (e.g. {@code -Dparallel=methods -DthreadCount=3}):
 *
 * <pre>
 * mvn test -Dgroups=mobile                                      # sequential, single device
 * mvn test -Dgroups=mobile -Dparallel=methods -DthreadCount=3   # pooled across every device
 * </pre>
 *
 * <p>Whichever device becomes free first picks up the next test - not "every device runs the
 * same test" (that's {@link MobileDeviceMatrix}/{@code MultiDeviceParallelTest}'s job), and
 * not "one device per thread regardless of load" - a genuine checkout/return pool, the same
 * shape a physical device lab or a Selenium Grid hub uses.</p>
 *
 * <p>Devices come from {@code androidList} and {@code iosList} in {@code
 * config/mobile-devices.json} combined ({@link MobileDeviceMatrix#androidList()} /
 * {@link MobileDeviceMatrix#iosList()}). {@link DriverFactory} checks out a device in
 * {@code @BeforeMethod} (via {@code MobileDriverManager.getDriver()}); the checkout blocks if
 * every device is currently busy, so a run with more threads than devices queues automatically
 * rather than oversubscribing a device. {@code DriverCleanupListener} quitting the driver
 * after every test returns that thread's device to the pool via
 * {@link #releaseForCurrentThread()}.</p>
 *
 * <p><b>Thread-safety:</b> a {@link LinkedBlockingQueue} (thread-safe singleton, category 2)
 * backs the pool; which device a thread currently holds is tracked in a {@code ThreadLocal}
 * (category 3), so one thread's checkout is never visible to another's.</p>
 */
final class MobileDevicePool {

    private static volatile BlockingQueue<MobileDeviceMatrix.Row> pool;
    private static final Object INIT_LOCK = new Object();

    private static final ThreadLocal<MobileDeviceMatrix.Row> CHECKED_OUT_BY_THREAD = new ThreadLocal<>();

    private MobileDevicePool() {
    }

    /** True when this run should draw from the device pool instead of the single active device. */
    static boolean isPooledRunActive() {
        return System.getProperty("parallel") != null;
    }

    /** Blocks until a device is free, then checks it out for the calling thread. */
    static MobileDeviceMatrix.Row checkout() {
        try {
            MobileDeviceMatrix.Row device = pool().take();
            CHECKED_OUT_BY_THREAD.set(device);
            return device;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DriverInitializationException("Interrupted while waiting for a free mobile device.", e);
        }
    }

    /** Returns the calling thread's checked-out device to the pool, if it holds one. No-op otherwise. */
    static void releaseForCurrentThread() {
        MobileDeviceMatrix.Row device = CHECKED_OUT_BY_THREAD.get();
        if (device != null) {
            pool().offer(device);
            CHECKED_OUT_BY_THREAD.remove();
        }
    }

    private static BlockingQueue<MobileDeviceMatrix.Row> pool() {
        BlockingQueue<MobileDeviceMatrix.Row> result = pool;
        if (result == null) {
            synchronized (INIT_LOCK) {
                result = pool;
                if (result == null) {
                    result = new LinkedBlockingQueue<>(loadPoolDevices());
                    pool = result;
                }
            }
        }
        return result;
    }

    private static List<MobileDeviceMatrix.Row> loadPoolDevices() {
        List<MobileDeviceMatrix.Row> devices = new ArrayList<>();
        for (String id : MobileDeviceMatrix.androidList()) {
            devices.add(MobileDeviceMatrix.loadDevice(id));
        }
        for (String id : MobileDeviceMatrix.iosList()) {
            devices.add(MobileDeviceMatrix.loadDevice(id));
        }
        if (devices.isEmpty()) {
            throw new ConfigurationException(
                    "'androidList' and 'iosList' in config/mobile-devices.json listed no devices between them.");
        }
        return devices;
    }
}
