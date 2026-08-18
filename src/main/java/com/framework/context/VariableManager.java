package com.framework.context;

import com.framework.exceptions.FrameworkException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Low-level, thread-safe runtime variable store backing all Phase 8 data
 * chaining (requirement.md &sect;11: "Provide a thread-safe context/variable
 * manager... Never allow API test execution in one thread to accidentally
 * consume another thread's values").
 *
 * <p>{@link com.framework.api.ApiContext} is the API-facing surface tests
 * actually call; this class is the shared mechanism underneath it, kept
 * general enough (package {@code com.framework.context}, not
 * {@code com.framework.api}) that a future Web/Mobile or cross-layer chaining
 * need (e.g. a value produced by a Web flow consumed by a later API call)
 * reuses it instead of a second parallel implementation (RULE 5).</p>
 *
 * <p><b>Thread-safety classification (requirement.md &sect;21):</b> backed by
 * a {@link ThreadLocal} map &mdash; category 3, thread-local. Each thread gets
 * its own independent variable set; nothing here is shared or synchronized
 * because nothing here is ever visible across threads by design.</p>
 */
public final class VariableManager {

    private static final ThreadLocal<Map<String, String>> VARIABLES = ThreadLocal.withInitial(HashMap::new);

    private VariableManager() {
    }

    /** Stores {@code value} under {@code key} for the calling thread, overwriting any existing value. */
    public static void set(String key, String value) {
        VARIABLES.get().put(key, value);
    }

    /** Returns the value stored for {@code key} on the calling thread, or throws if none was ever set. */
    public static String get(String key) {
        String value = VARIABLES.get().get(key);
        if (value == null) {
            throw new FrameworkException(
                    "No runtime variable named '" + key + "' has been set on this thread. "
                            + "It must be produced by an earlier call (e.g. via ApiContext.set(\"" + key
                            + "\", ...)) before it can be read or referenced as ${{" + key + "}}.");
        }
        return value;
    }

    /** Same as {@link #get(String)} but returns an empty {@link Optional} instead of throwing when unset. */
    public static Optional<String> getOptional(String key) {
        return Optional.ofNullable(VARIABLES.get().get(key));
    }

    public static boolean contains(String key) {
        return VARIABLES.get().containsKey(key);
    }

    /** Removes a single variable for the calling thread; a no-op if it was never set. */
    public static void remove(String key) {
        VARIABLES.get().remove(key);
    }

    /**
     * Removes every variable for the calling thread and detaches the ThreadLocal entry itself.
     * Must run on test/thread completion so pooled threads under {@code parallel="methods"}
     * never leak one test's chained values into the next (requirement.md &sect;33; see
     * {@link com.framework.listeners.ApiContextListener}).
     */
    public static void clear() {
        VARIABLES.remove();
    }
}
