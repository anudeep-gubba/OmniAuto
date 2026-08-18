package com.framework.driver;

import com.fasterxml.jackson.databind.JsonNode;
import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.exceptions.ConfigurationException;
import com.framework.utils.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads every local mobile device this framework knows about from a single shared JSON file,
 * {@code config/mobile-devices.json} - one file, not duplicated per {@code config/{env}.properties}
 * (the same local emulator/simulator is used regardless of {@code -Denv}), and a much better
 * shape than flat properties for a list of multi-field records:
 *
 * <pre>
 * {
 *   "devices": {
 *     "android1": { "platform": "android", "deviceName": "Pixel_10", "platformVersion": "17" },
 *     "ios1":     { "platform": "ios", "deviceName": "iPhone 17 Pro", "platformVersion": "26.2" }
 *   },
 *   "androidList": ["android1"],
 *   "iosList": ["ios1"],
 *   "matrices": { "cross-platform": ["android1", "ios1"] },
 *   "ports": { "systemPort": { "start": 8200, "count": 50 } }
 * }
 * </pre>
 *
 * <p>Which app binary to install is deliberately <b>not</b> in this file: it's an
 * environment/build concern (a real project might run a different build per environment),
 * not device inventory, so it lives in {@code config/{env}.properties} instead -
 * {@link ConfigKeys#MOBILE_APP_PATH_ANDROID}/{@link ConfigKeys#MOBILE_APP_PATH_IOS}, one entry
 * per <b>platform</b>, not per device (every device on a platform runs the identical build).</p>
 *
 * <ul>
 *     <li>{@code devices} - every device, keyed by id, defined once. Two optional fields:
 *     {@code appiumServerUrl} overrides the shared {@code appium.server.url} for that one
 *     device (a real multi-machine device lab; omit for the common case), and {@code hybrid}
 *     (Android only) requests a {@code chromedriverPort} alongside {@code systemPort} for a
 *     WebView/Chrome-hybrid app.</li>
 *     <li>{@code ports} - the {@code start}/{@code count} pool range for {@code systemPort},
 *     {@code wdaLocalPort}, and {@code chromedriverPort} (see {@link MobilePortAllocator}).
 *     <b>Not configurable per device</b>, deliberately: {@link MobilePortAllocator} checks out
 *     a fresh port from these ranges per driver creation and returns it after, rather than a
 *     fixed port pinned to a device - pinning one would reintroduce a real bug this framework
 *     already hit once (a stale UiAutomator2 server left bound to a cached port collided with
 *     a same-port retry - see {@code MobilePortAllocator}'s Javadoc).</li>
 *     <li>{@code androidList} / {@code iosList} - every id grouped by platform; a sequential
 *     (non {@code -Dparallel}) mobile run uses {@code mobile.platform} to pick one of these
 *     and takes its first id (see {@link DriverFactory}); a {@code -Dparallel} run draws from
 *     both combined as a work queue (see {@link MobileDevicePool}).</li>
 *     <li>{@code matrices} - named lists of ids for "run the same test on every device at
 *     once" ({@code MultiDeviceParallelTest}), not a work queue.</li>
 * </ul>
 */
public final class MobileDeviceMatrix {

    private static final String RESOURCE_PATH = "config/mobile-devices.json";

    private static volatile JsonNode root;
    private static final Object INIT_LOCK = new Object();

    private MobileDeviceMatrix() {
    }

    /**
     * One device; {@code id} is its key under {@code devices} in {@code mobile-devices.json}.
     * {@code appiumServerUrl} is {@code null} unless that device names its own (falls back to
     * the shared {@code appium.server.url} - see {@link DriverFactory}). {@code hybrid}
     * (Android only) requests a {@code chromedriverPort} be allocated alongside
     * {@code systemPort}, for an app with WebView/Chrome-hybrid content.
     */
    public record Row(String id, String platform, String deviceName, String platformVersion, String appPath,
                       String appiumServerUrl, boolean hybrid) {
    }

    /**
     * Resolves a single device by id from {@code devices}, with its platform's app path from
     * {@code config/{env}.properties} ({@link ConfigKeys#MOBILE_APP_PATH_ANDROID}/
     * {@link ConfigKeys#MOBILE_APP_PATH_IOS} - an environment/build concern, not device
     * inventory, so it isn't in this JSON file at all).
     */
    public static Row loadDevice(String id) {
        JsonNode device = root().path("devices").path(id);
        if (device.isMissingNode()) {
            throw new ConfigurationException("No device '" + id + "' defined in '" + RESOURCE_PATH + "'.");
        }
        String platform = requiredField(device, "platform", id);
        return new Row(
                id,
                platform,
                requiredField(device, "deviceName", id),
                requiredField(device, "platformVersion", id),
                appPathForPlatform(platform),
                device.path("appiumServerUrl").asText(null),
                device.path("hybrid").asBoolean(false));
    }

    /** One port pool's range: {@code count} ports starting at {@code start}. */
    public record PortRange(int start, int count) {
    }

    /**
     * The {@code start}/{@code count} configured under {@code ports.<portType>} (e.g.
     * {@code "systemPort"}), or the given defaults if {@code ports} or that entry is absent.
     */
    public static PortRange portRange(String portType, int defaultStart, int defaultCount) {
        JsonNode node = root().path("ports").path(portType);
        return new PortRange(node.path("start").asInt(defaultStart), node.path("count").asInt(defaultCount));
    }

    /** Every id in {@code androidList}, in declared order. */
    public static List<String> androidList() {
        return idsAt("androidList");
    }

    /** Every id in {@code iosList}, in declared order. */
    public static List<String> iosList() {
        return idsAt("iosList");
    }

    /** Every device in {@code matrices.<matrixName>}, resolved, in declared order. */
    public static List<Row> load(String matrixName) {
        JsonNode ids = root().path("matrices").path(matrixName);
        if (!ids.isArray() || ids.isEmpty()) {
            throw new ConfigurationException(
                    "No matrix '" + matrixName + "' (or it lists no devices) under 'matrices' in '"
                            + RESOURCE_PATH + "'.");
        }
        List<Row> rows = new ArrayList<>();
        for (JsonNode idNode : ids) {
            rows.add(loadDevice(idNode.asText()));
        }
        return rows;
    }

    /** {@link #load(String)}, packaged as a TestNG {@code @DataProvider} row set (one {@link Row} argument each). */
    public static Object[][] dataProvider(String matrixName) {
        List<Row> rows = load(matrixName);
        Object[][] result = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            result[i][0] = rows.get(i);
        }
        return result;
    }

    private static String appPathForPlatform(String platform) {
        String key = "ios".equalsIgnoreCase(platform) ? ConfigKeys.MOBILE_APP_PATH_IOS : ConfigKeys.MOBILE_APP_PATH_ANDROID;
        return ConfigManager.getString(key);
    }

    private static List<String> idsAt(String field) {
        JsonNode ids = root().path(field);
        List<String> result = new ArrayList<>();
        for (JsonNode idNode : ids) {
            result.add(idNode.asText());
        }
        return result;
    }

    private static String requiredField(JsonNode device, String field, String deviceId) {
        String value = device.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException(
                    "Device '" + deviceId + "' in '" + RESOURCE_PATH + "' is missing '" + field + "'.");
        }
        return value;
    }

    /**
     * Parsed once (double-checked lazy init) and cached for the process lifetime - category 1,
     * immutable and globally shareable, same classification as {@link
     * com.framework.config.ConfigManager}'s global config tier.
     */
    private static JsonNode root() {
        JsonNode result = root;
        if (result == null) {
            synchronized (INIT_LOCK) {
                result = root;
                if (result == null) {
                    result = parse();
                    root = result;
                }
            }
        }
        return result;
    }

    private static JsonNode parse() {
        try (InputStream in = MobileDeviceMatrix.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new ConfigurationException("Classpath resource '" + RESOURCE_PATH + "' not found.");
            }
            return JsonUtils.objectMapper().readTree(in);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to parse '" + RESOURCE_PATH + "': " + e.getMessage(), e);
        }
    }
}
