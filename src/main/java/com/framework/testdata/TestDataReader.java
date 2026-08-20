package com.framework.testdata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One format-specific parser (JSON, YAML, CSV, Excel) behind {@link TestDataManager}'s
 * uniform {@code load(...)} entry point - requirement.md &sect;15: "The test should not
 * care whether the source is JSON / YAML / Excel / CSV."
 *
 * <p>Every implementation normalizes its source into the same shape: a list of
 * <b>records</b>, each a {@code Map<String, Object>} of raw, placeholder-unresolved values -
 * nested where the source itself nests (JSON/YAML objects), or via {@link #unflatten} where
 * the source is inherently flat (a CSV/Excel row) but still needs to carry a nested shape like
 * {@code (metadata, data)} - see {@link CsvDataReader}/{@link ExcelDataReader} for the dotted
 * column convention that drives it. Two source shapes collapse onto this uniformly (see each
 * implementation's javadoc for specifics):</p>
 * <ul>
 *     <li><b>Row-oriented</b> sources (CSV, Excel, or a JSON/YAML array) - each row/element
 *     is already one record.</li>
 *     <li><b>Name-keyed</b> sources (a JSON/YAML object whose top-level keys are test-case
 *     names, e.g. {@code {"validLogin": {...}, "invalidLogin": {...}}}) - each entry becomes
 *     one record, with its key injected into the record under {@link #NAME_FIELD} so
 *     {@link TestData#get(String)} can look it up the same way regardless of source format.</li>
 * </ul>
 *
 * <p>Deliberately returns raw values, not placeholder-resolved ones: {@link TestDataManager}
 * caches this raw result once per resource path and {@link TestData} resolves
 * {@code ${{...}}} placeholders fresh on every access, so a runtime variable
 * (e.g. {@code ${{eventId}}}) produced after the file was first loaded still resolves
 * correctly (requirement.md &sect;14).</p>
 */
interface TestDataReader {

    /** The record field {@link TestDataManager}/{@link TestData} treat as a record's lookup name. */
    String NAME_FIELD = "name";

    /** Parses {@code classpathResource} into raw records. Never returns {@code null}. */
    List<Map<String, Object>> read(String classpathResource);

    /**
     * Expands a flat row's dotted keys (e.g. {@code "metadata.testCaseId"}) into nested maps,
     * so a CSV/Excel row can carry the same {@code (metadata, data)}-nested shape a JSON/YAML
     * object naturally does - which is what makes a row convertible into a {@code *TestCase}
     * record ({@link TestCaseRecord}) via {@link TestDataManager#getCaseData}, the same as any
     * other format. Only splits on the <em>first</em> dot in a key - one level deep, which is
     * as deep as every {@code *TestCase} shape this framework defines actually goes (a
     * top-level segment like {@code metadata}/{@code data}, flat fields beneath it). A key with
     * no dot (e.g. {@link #NAME_FIELD} itself) stays a flat top-level entry, unchanged.
     *
     * <p><b>A blank cell is dropped, not kept as an empty string</b> - it means "this field is
     * unset", the same as a JSON/YAML record simply omitting the key (e.g. {@code
     * EventPayloadTestCase.EventPayloadData#eventDate} is unset on every case except one that
     * specifically needs a fixed literal date, and code branches on {@code eventDate() != null}
     * - a row-oriented format has no way to omit a column on just one row, so a blank cell is
     * the only way to express "not set" at all). A case that genuinely needs an intentional
     * empty-string value (e.g. a blank-credentials login case) should stay in JSON/YAML, where
     * absent-vs-empty-string is representable directly.</p>
     */
    static Map<String, Object> unflatten(Map<String, String> flatRow) {
        Map<String, Object> nested = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : flatRow.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            int dot = entry.getKey().indexOf('.');
            if (dot < 0) {
                nested.put(entry.getKey(), entry.getValue());
                continue;
            }
            String parent = entry.getKey().substring(0, dot);
            String child = entry.getKey().substring(dot + 1);
            @SuppressWarnings("unchecked")
            Map<String, Object> parentMap =
                    (Map<String, Object>) nested.computeIfAbsent(parent, key -> new LinkedHashMap<String, Object>());
            parentMap.put(child, entry.getValue());
        }
        return nested;
    }
}
