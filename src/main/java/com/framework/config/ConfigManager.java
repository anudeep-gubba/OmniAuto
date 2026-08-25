package com.framework.config;

import com.framework.constants.ConfigKeys;
import com.framework.enums.Environment;
import com.framework.exceptions.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Central configuration access point for the entire framework.
 *
 * <p>Every value is resolved through a fixed 4-tier precedence chain
 * (highest wins), matching requirement.md &sect;12:</p>
 * <ol>
 *     <li>Test-specific override &mdash; {@link #setOverride(String, String)} (thread-local)</li>
 *     <li>TestNG {@code <parameter>} values &mdash; {@link #setTestNgParameters(Map)} (thread-local)</li>
 *     <li>JVM system property, e.g. {@code -Dbrowser=chrome}</li>
 *     <li>{@code config/{env}.properties}</li>
 * </ol>
 *
 * <p>No separate {@code default.properties} layer: each {@code config/{env}.properties}
 * file is fully self-contained (RULE 6/tester-friendliness - one file to read, no hidden
 * merge). {@code qa} is the default environment when {@code -Denv} is not given.</p>
 *
 * <p><b>Thread-safety classification (requirement.md &sect;21):</b> the merged
 * environment/system-property snapshot is computed once (double-checked
 * lazy init) and then treated as <b>immutable and globally shareable</b>
 * (category 1) for the process lifetime. The TestNG-parameter and test-override
 * layers are <b>thread-local</b> (category 3) so parallel tests never see each
 * other's values &mdash; see {@link com.framework.listeners.ConfigParameterListener}
 * for how tier 4 gets populated per test method.</p>
 *
 * <p><b>Fail-fast:</b> an unsupported {@code env}, a missing
 * {@code config/{env}.properties} file, or a missing/blank required key throws
 * {@link ConfigurationException} immediately on first access &mdash; never mid-test
 * (requirement.md &sect;31).</p>
 */
public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);

    private static final String CONFIG_RESOURCE_PATH = "config/";
    private static final String DEFAULT_ENVIRONMENT = "qa";
    private static final List<String> REQUIRED_KEYS =
            List.of(ConfigKeys.BROWSER, ConfigKeys.BASE_URL, ConfigKeys.API_BASE_URL);

    private static volatile Map<String, String> globalConfig;
    private static final Object INIT_LOCK = new Object();

    private static final ThreadLocal<Map<String, String>> TESTNG_PARAMETERS = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Map<String, String>> TEST_OVERRIDES = ThreadLocal.withInitial(HashMap::new);

    private ConfigManager() {
    }

    // ------------------------------------------------------------------
    // Public read API
    // ------------------------------------------------------------------

    public static String getString(String key) {
        String value = resolve(key);
        if (value == null) {
            throw new ConfigurationException(
                    "Missing required configuration key '" + key + "'. Checked, highest precedence first: "
                            + "test-override, TestNG <parameter>, system property -D" + key + ", "
                            + getEnvironment().name().toLowerCase() + ".properties.");
        }
        return value;
    }

    public static String getString(String key, String defaultValue) {
        String value = resolve(key);
        return value != null ? value : defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        String value = resolve(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException(
                    "Configuration key '" + key + "' must be an integer but was '" + value + "'.", e);
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = resolve(key);
        return value != null ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    public static Environment getEnvironment() {
        return Environment.fromString(resolve(ConfigKeys.ENV));
    }

    public static String getBrowser() {
        return getString(ConfigKeys.BROWSER);
    }

    public static boolean isHeadless() {
        return getBoolean(ConfigKeys.HEADLESS, false);
    }

    public static String getResolution() {
        return getString(ConfigKeys.RESOLUTION, "1920x1080");
    }

    public static String getBaseUrl() {
        return getString(ConfigKeys.BASE_URL);
    }

    public static String getApiBaseUrl() {
        return getString(ConfigKeys.API_BASE_URL);
    }

    /** {@code true} (default) -&gt; every run replaces {@code reports/extent/index.html} and {@code reports/api/index.html}; {@code false} -&gt; each run writes its own timestamped file instead. See {@link ConfigKeys#REPORT_OVERWRITE}. */
    public static boolean isReportOverwriteEnabled() {
        return getBoolean(ConfigKeys.REPORT_OVERWRITE, true);
    }

    /** Branding for the API report only (reports/api/) - see {@link ConfigKeys#REPORT_API_TITLE}. */
    public static String getApiReportTitle() {
        return getString(ConfigKeys.REPORT_API_TITLE, "EventHub API Automation Report");
    }

    /** Branding for the API report only (reports/api/) - see {@link ConfigKeys#REPORT_API_NAME}. */
    public static String getApiReportName() {
        return getString(ConfigKeys.REPORT_API_NAME, "EventHub API Automation Framework");
    }

    // ------------------------------------------------------------------
    // Thread-scoped write API (tiers 4 and 5)
    // ------------------------------------------------------------------

    /** Populates tier 4 (TestNG {@code <parameter>} values) for the calling thread. */
    public static void setTestNgParameters(Map<String, String> parameters) {
        TESTNG_PARAMETERS.set(new HashMap<>(parameters));
    }

    /** Sets a tier-5, highest-precedence override visible only to the calling thread. */
    public static void setOverride(String key, String value) {
        TEST_OVERRIDES.get().put(key, value);
    }

    /**
     * Removes all thread-local configuration state (tiers 4 and 5) for the calling thread.
     * Must run on test/thread completion so ThreadLocal entries never leak into a
     * different test reusing the same pooled thread (requirement.md &sect;33).
     */
    public static void clearThreadState() {
        TESTNG_PARAMETERS.remove();
        TEST_OVERRIDES.remove();
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    private static String resolve(String key) {
        String override = TEST_OVERRIDES.get().get(key);
        if (override != null) {
            return override;
        }
        String parameter = TESTNG_PARAMETERS.get().get(key);
        if (parameter != null) {
            return parameter;
        }
        String global = globalConfig().get(key);
        if (global != null) {
            return global;
        }
        // Ad-hoc system property not predeclared in any properties file, e.g. a one-off -Dkey=value.
        return System.getProperty(key);
    }

    private static Map<String, String> globalConfig() {
        Map<String, String> result = globalConfig;
        if (result == null) {
            synchronized (INIT_LOCK) {
                result = globalConfig;
                if (result == null) {
                    result = loadAndValidate();
                    globalConfig = result;
                }
            }
        }
        return result;
    }

    private static Map<String, String> loadAndValidate() {
        String rawEnv = System.getProperty(ConfigKeys.ENV, DEFAULT_ENVIRONMENT);
        Environment environment = Environment.fromString(rawEnv);
        String envFile = environment.name().toLowerCase() + ".properties";

        Map<String, String> merged = new HashMap<>(loadProperties(envFile, true));
        merged.put(ConfigKeys.ENV, environment.name().toLowerCase());

        // Tier 3: a system property overrides any key already known from the file above.
        for (String key : new HashMap<>(merged).keySet()) {
            String systemValue = System.getProperty(key);
            if (systemValue != null) {
                merged.put(key, systemValue);
            }
        }

        validateRequiredKeys(merged, environment, envFile);

        LOGGER.info("Configuration loaded for environment '{}' from '{}' (system-property overrides applied).",
                environment, envFile);

        return Collections.unmodifiableMap(merged);
    }

    private static Map<String, String> loadProperties(String fileName, boolean required) {
        String resource = CONFIG_RESOURCE_PATH + fileName;
        Properties properties = new Properties();
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                if (required) {
                    throw new ConfigurationException(
                            "Required configuration file '" + resource + "' was not found on the classpath. "
                                    + "Expected at " + resource + " (repo root, not under src/main/resources - "
                                    + "see pom.xml's <resources> and README.md's Environment configuration).");
                }
                return Map.of();
            }
            properties.load(in);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read configuration file '" + resource + "'.", e);
        }

        Map<String, String> result = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            result.put(name, properties.getProperty(name));
        }
        return result;
    }

    private static void validateRequiredKeys(Map<String, String> merged, Environment environment, String envFile) {
        List<String> missing = REQUIRED_KEYS.stream()
                .filter(key -> merged.get(key) == null || merged.get(key).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw new ConfigurationException(
                    "Configuration validation failed for environment '" + environment + "'. "
                            + "Missing/blank required key(s): " + missing + ". Define them in config/" + envFile + ".");
        }
    }
}
