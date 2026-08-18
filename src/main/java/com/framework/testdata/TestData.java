package com.framework.testdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exceptions.TestDataException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of {@link TestDataManager#load(String)} - a source-agnostic view over whatever
 * {@link TestDataReader} parsed the file into, exactly as requirement.md &sect;15 asks for
 * ("the test should not care whether the source is JSON/YAML/Excel/CSV").
 *
 * <pre>
 * LoginData data = TestDataManager.load("login.json").get("validLogin", LoginData.class);
 * </pre>
 *
 * <p><b>Thread-safety / immutability (requirement.md &sect;15, &sect;21):</b> wraps the same
 * raw-records list {@link TestDataManager} caches globally per resource path - <b>category 1,
 * immutable and globally shareable</b>, the same classification as {@link
 * com.framework.config.ConfigManager}'s global config tier. Every accessor here builds and
 * returns a brand-new {@link Map}/object; nothing about the shared raw list is ever mutated,
 * so one parallel test can never corrupt test data another test is reading from the same
 * cached file (requirement.md &sect;15: "Do not allow one parallel test to mutate shared test
 * data used by another test").</p>
 *
 * <p><b>Placeholder resolution happens here, lazily, not in {@link TestDataReader}:</b> each
 * accessor resolves {@code ${{...}}} tokens fresh against whatever secrets/config/runtime
 * {@code ApiContext} values are current <em>at access time</em>, not at load time. This
 * matters for chaining test data - e.g. a booking request file containing
 * {@code ${{eventId}}} - where the runtime value the placeholder needs is only known after an
 * earlier API call in the same test, well after the file may have first been cached.</p>
 */
public final class TestData {

    private final List<Map<String, Object>> rawRecords;
    private final ObjectMapper conversionMapper;
    private final String sourceDescription;

    TestData(List<Map<String, Object>> rawRecords, ObjectMapper conversionMapper, String sourceDescription) {
        this.rawRecords = rawRecords;
        this.conversionMapper = conversionMapper;
        this.sourceDescription = sourceDescription;
    }

    public int size() {
        return rawRecords.size();
    }

    /** The resolved record at {@code index} (0-based, in file order). */
    public Map<String, Object> get(int index) {
        return resolve(rawRecordAt(index));
    }

    /** {@code index}'s record, deserialized into {@code type} via a lenient scalar-coercing mapper. */
    public <T> T get(int index, Class<T> type) {
        return convert(get(index), type);
    }

    /**
     * The resolved record whose {@link TestDataReader#NAME_FIELD} equals {@code name} - a
     * JSON/YAML object key, or a CSV/Excel row's {@code name} column.
     */
    public Map<String, Object> get(String name) {
        return resolve(rawRecordNamed(name));
    }

    /** {@code name}'s record, deserialized into {@code type} via a lenient scalar-coercing mapper. */
    public <T> T get(String name, Class<T> type) {
        return convert(get(name), type);
    }

    /** Every record, resolved, in file order. */
    public List<Map<String, Object>> all() {
        List<Map<String, Object>> resolved = new ArrayList<>(rawRecords.size());
        for (Map<String, Object> raw : rawRecords) {
            resolved.add(resolve(raw));
        }
        return resolved;
    }

    /** One TestNG {@code @DataProvider} row per record, each row a single {@code Map<String, Object>} argument. */
    public Object[][] dataProvider() {
        List<Map<String, Object>> resolved = all();
        Object[][] rows = new Object[resolved.size()][1];
        for (int i = 0; i < resolved.size(); i++) {
            rows[i][0] = resolved.get(i);
        }
        return rows;
    }

    /** Same as {@link #dataProvider()}, but each row's single argument is deserialized into {@code type}. */
    public <T> Object[][] dataProvider(Class<T> type) {
        List<Map<String, Object>> resolved = all();
        Object[][] rows = new Object[resolved.size()][1];
        for (int i = 0; i < resolved.size(); i++) {
            rows[i][0] = convert(resolved.get(i), type);
        }
        return rows;
    }

    private Map<String, Object> rawRecordAt(int index) {
        if (index < 0 || index >= rawRecords.size()) {
            throw new TestDataException(
                    "No record at index " + index + " in " + sourceDescription + " (" + rawRecords.size() + " record(s)).");
        }
        return rawRecords.get(index);
    }

    private Map<String, Object> rawRecordNamed(String name) {
        for (Map<String, Object> record : rawRecords) {
            if (name.equals(record.get(TestDataReader.NAME_FIELD))) {
                return record;
            }
        }
        throw new TestDataException(
                "No record named '" + name + "' in " + sourceDescription + ". Add a '" + TestDataReader.NAME_FIELD
                        + "' field (or, for JSON/YAML, a top-level key) with that name.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolve(Map<String, Object> raw) {
        return (Map<String, Object>) resolveValue(raw);
    }

    /** Recursively resolves {@code ${{...}}} placeholders in every String leaf of a record's value tree. */
    private static Object resolveValue(Object value) {
        if (value instanceof String s) {
            return PlaceholderResolver.resolve(s);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, v) -> resolved.put(String.valueOf(key), resolveValue(v)));
            return resolved;
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>(list.size());
            for (Object element : list) {
                resolved.add(resolveValue(element));
            }
            return resolved;
        }
        return value;
    }

    private <T> T convert(Map<String, Object> resolvedRecord, Class<T> type) {
        try {
            return conversionMapper.convertValue(resolvedRecord, type);
        } catch (IllegalArgumentException e) {
            throw new TestDataException(
                    "Failed to convert a record from " + sourceDescription + " into " + type.getSimpleName()
                            + ": " + e.getMessage(), e);
        }
    }
}
