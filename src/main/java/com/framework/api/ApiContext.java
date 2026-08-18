package com.framework.api;

import com.framework.context.VariableManager;
import com.framework.secrets.SensitiveDataMasker;
import com.framework.testdata.PlaceholderResolver;

import java.util.Optional;

/**
 * The API-facing runtime-variable surface requirement.md &sect;11 describes:
 *
 * <pre>
 * apiContext.set("userId", userId);
 * String userId = apiContext.get("userId");
 * </pre>
 *
 * <p>A thin, API-specific facade over {@link VariableManager} (the shared,
 * general-purpose thread-safe store) rather than a second storage
 * implementation - kept as a static utility class to match every other
 * framework-wide access point ({@code ConfigManager}, {@code SecretManager},
 * {@link ApiClient}) instead of introducing a differently-shaped API for this
 * one case.</p>
 *
 * <p><b>Self-registers as a {@link PlaceholderResolver} source</b> so any
 * value stored here is automatically resolvable as {@code ${{key}}} in test
 * data or request bodies (requirement.md &sect;14) with no separate wiring
 * step. Registration happens in a static initializer, which is safe without
 * an explicit framework-bootstrap hook: a chained value can only be
 * <i>referenced</i> as {@code ${{userId}}} after something already called
 * {@link #set(String, String)} to produce it, and that call is what first
 * loads this class - so the source is always registered before it could
 * possibly be needed.</p>
 *
 * <p><b>Absorbs {@link ApiClient}'s bearer-token storage</b> (its Phase 7
 * javadoc flagged this as deferred to Phase 8): the current thread's token is
 * just another context variable, stored under {@link #ACCESS_TOKEN_KEY}, so
 * it chains and resolves via {@code ${{accessToken}}} the same as any
 * server-generated {@code userId}/{@code orderId}. {@link ApiClient}'s public
 * API is unchanged.</p>
 */
public final class ApiContext {

    /** Reserved key {@link ApiClient} stores the current thread's bearer token under. */
    public static final String ACCESS_TOKEN_KEY = "accessToken";

    static {
        PlaceholderResolver.registerSource(ApiContext::getOptional);
    }

    private ApiContext() {
    }

    /** Stores {@code value} under {@code key} for this thread; overwrites any existing value. */
    public static void set(String key, String value) {
        VariableManager.set(key, value);
        if (ACCESS_TOKEN_KEY.equals(key) && value != null) {
            // Defense in depth beyond the existing "Authorization" key-pattern masking
            // (SensitiveDataMasker): the raw token can now also flow unprefixed into any
            // ${{accessToken}}-templated request body, so mask it by literal value too.
            SensitiveDataMasker.registerSecretValue(value);
        }
    }

    /** Returns the value stored under {@code key} for this thread, or throws if it was never set. */
    public static String get(String key) {
        return VariableManager.get(key);
    }

    public static Optional<String> getOptional(String key) {
        return VariableManager.getOptional(key);
    }

    public static boolean has(String key) {
        return VariableManager.contains(key);
    }

    public static void remove(String key) {
        VariableManager.remove(key);
    }

    /** Clears every variable for this thread, including the access token. Also removes ThreadLocal state entirely. */
    public static void clear() {
        VariableManager.clear();
    }
}
