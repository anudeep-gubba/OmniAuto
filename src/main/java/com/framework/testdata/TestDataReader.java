package com.framework.testdata;

import java.util.List;
import java.util.Map;

/**
 * One format-specific parser (JSON, YAML, CSV, Excel) behind {@link TestDataManager}'s
 * uniform {@code load(...)} entry point - requirement.md &sect;15: "The test should not
 * care whether the source is JSON / YAML / Excel / CSV."
 *
 * <p>Every implementation normalizes its source into the same shape: a list of
 * <b>records</b>, each a flat {@code Map<String, Object>} of raw, placeholder-unresolved
 * values. Two source shapes collapse onto this uniformly (see each implementation's
 * javadoc for specifics):</p>
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
}
