package com.framework.testdata;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.framework.config.ConfigManager;
import com.framework.constants.ConfigKeys;
import com.framework.exceptions.TestDataException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single entry point for loading test data, regardless of source format
 * (requirement.md &sect;15):
 *
 * <pre>
 * TestDataManager.load("login.json").get("validLogin", LoginData.class);
 * TestDataManager.load("events.csv").dataProvider(); // straight into a TestNG @DataProvider
 * </pre>
 *
 * <p>Dispatches to a {@link TestDataReader} by file extension, under a fixed
 * per-format folder convention so a bare filename is enough (matching requirement.md's own
 * {@code testDataManager.load("login.json")} example):</p>
 * <pre>
 * *.json        -&gt; src/test/resources/testdata/json/
 * *.yaml, *.yml -&gt; src/test/resources/testdata/yaml/
 * *.csv         -&gt; src/test/resources/testdata/csv/
 * *.xlsx, *.xls -&gt; src/test/resources/testdata/excel/
 * </pre>
 *
 * <p><b>Which format a bare name reads from is configurable</b>, not hardcoded per call
 * site: a name with no extension (e.g. {@code "login"}) resolves against
 * {@link ConfigKeys#TEST_DATA_FORMAT} (a {@code config/{env}.properties} key, {@code json}
 * unless set) instead of requiring an extension in the call:</p>
 * <pre>
 * TestDataManager.load("login"); // testdata.format=json  -&gt; testdata/json/login.json
 *                                 // testdata.format=yaml  -&gt; testdata/yaml/login.yaml
 * </pre>
 * <p>A name that already carries a recognized extension (e.g. {@code "login.json"}) always
 * wins over the config value - this only fills in a missing one, so existing call sites and
 * tests that pin a specific format (e.g. {@code TestDataManagerTest}'s CSV vs. Excel cases)
 * are unaffected.</p>
 *
 * <p><b>Thread-safety (requirement.md &sect;15/&sect;21):</b> each resource path's raw,
 * placeholder-unresolved records are parsed once and cached in a {@link ConcurrentHashMap} -
 * <b>category 1, immutable and globally shareable</b>, the same classification as
 * {@link com.framework.config.ConfigManager}'s global config tier. Safe to share because
 * nothing downstream ever mutates the cached list; see {@link TestData}'s javadoc for how
 * per-access resolution keeps that true.</p>
 */
public final class TestDataManager {

    private static final String BASE_FOLDER = "testdata";

    private static final Map<String, TestDataReader> READERS_BY_EXTENSION = Map.of(
            "json", new JsonDataReader(),
            "yaml", new YamlDataReader(),
            "yml", new YamlDataReader(),
            "csv", new CsvDataReader(),
            "xlsx", new ExcelDataReader(),
            "xls", new ExcelDataReader()
    );

    private static final Map<String, String> FORMAT_FOLDER_BY_EXTENSION = Map.of(
            "json", "json",
            "yaml", "yaml",
            "yml", "yaml",
            "csv", "csv",
            "xlsx", "excel",
            "xls", "excel"
    );

    /** The extension a {@link ConfigKeys#TEST_DATA_FORMAT} value resolves to for a bare file name. */
    private static final Map<String, String> EXTENSION_BY_FORMAT = Map.of(
            "json", "json",
            "yaml", "yaml",
            "yml", "yaml",
            "csv", "csv",
            "excel", "xlsx"
    );

    /**
     * A separate mapper from {@link com.framework.utils.JsonUtils}'s API-response one:
     * intentionally lenient about scalar coercion (a CSV/Excel cell's {@code "50"} converting
     * cleanly into an {@code int} field) since spreadsheet/CSV sources are inherently
     * stringly-typed. Kept private to this class rather than loosening the shared API mapper,
     * so real API response parsing stays strict (requirement.md &sect;21 - deliberately not a
     * shared mutable global here beyond its own immutable configuration).
     */
    private static final ObjectMapper CONVERSION_MAPPER = buildConversionMapper();

    private static ObjectMapper buildConversionMapper() {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.coercionConfigDefaults().setCoercion(CoercionInputShape.String, CoercionAction.TryConvert);
        return mapper;
    }

    private static final Map<String, List<Map<String, Object>>> CACHE = new ConcurrentHashMap<>();

    private TestDataManager() {
    }

    /**
     * Loads (or returns the already-cached parse of) {@code fileName}, e.g. {@code "login.json"}.
     * A {@code fileName} with no extension (e.g. {@code "login"}) resolves against the
     * configured default format instead - see {@link ConfigKeys#TEST_DATA_FORMAT}.
     */
    public static TestData load(String fileName) {
        String resolvedFileName = resolveFileName(fileName);
        String extension = extensionOf(resolvedFileName);
        TestDataReader reader = READERS_BY_EXTENSION.get(extension);
        if (reader == null) {
            throw new TestDataException(
                    "Unsupported test data file extension '." + extension + "' for '" + resolvedFileName
                            + "'. Supported: " + READERS_BY_EXTENSION.keySet() + ".");
        }
        String resourcePath = BASE_FOLDER + "/" + FORMAT_FOLDER_BY_EXTENSION.get(extension) + "/" + resolvedFileName;
        List<Map<String, Object>> rawRecords = CACHE.computeIfAbsent(resourcePath, reader::read);
        return new TestData(rawRecords, CONVERSION_MAPPER, resourcePath);
    }

    /**
     * Fills in a missing extension from {@link ConfigKeys#TEST_DATA_FORMAT} (default
     * {@code json}); a {@code fileName} that already has one is returned unchanged, so it
     * always takes precedence over the configured default.
     */
    private static String resolveFileName(String fileName) {
        if (fileName.lastIndexOf('.') > 0) {
            return fileName;
        }
        String format = ConfigManager.getString(ConfigKeys.TEST_DATA_FORMAT, "json").toLowerCase(Locale.ROOT);
        String extension = EXTENSION_BY_FORMAT.get(format);
        if (extension == null) {
            throw new TestDataException(
                    "Unsupported '" + ConfigKeys.TEST_DATA_FORMAT + "' value '" + format + "' for '" + fileName
                            + "'. Supported: " + EXTENSION_BY_FORMAT.keySet() + ".");
        }
        return fileName + "." + extension;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new TestDataException("Test data file name '" + fileName + "' has no extension to dispatch on.");
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
