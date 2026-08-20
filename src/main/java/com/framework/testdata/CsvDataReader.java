package com.framework.testdata;

import com.framework.exceptions.TestDataException;
import com.framework.utils.FileUtils;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code .csv} test data: naturally row-oriented, so every row is one record, keyed
 * by its header cell (opencsv's {@link CSVReaderHeaderAware} does the header-to-map binding
 * directly - no manual header/index bookkeeping). A column literally named {@code name}
 * (see {@link TestDataReader#NAME_FIELD}) opts a CSV file into {@link TestData#get(String)}
 * lookups the same way a JSON/YAML object-root file's keys do.
 *
 * <pre>
 * name,city,category
 * validEvent,Testville,Conference
 * invalidEvent,,Conference
 * </pre>
 *
 * <p>A dotted column name (e.g. {@code metadata.testCaseId}, {@code data.email}) nests via
 * {@link TestDataReader#unflatten} - the convention that lets a CSV row carry the same
 * {@code (metadata, data)}-shaped {@code *TestCase} record JSON/YAML already can:</p>
 *
 * <pre>
 * name,metadata.testCaseId,metadata.testCaseName,data.email,data.password
 * validLogin,TC-WEB-LOGIN-001,User logs in successfully...,${{EVENTHUB_EMAIL}},${{EVENTHUB_PASSWORD}}
 * </pre>
 *
 * <p>All values come back as {@link String} - opencsv does not infer types, and neither
 * does this reader (consistent with, e.g., {@code EventResponse.price()} already modeling
 * a numeric-looking API field as {@code String} rather than guessing - see Phase 7). Typed
 * access via {@link TestData#get(String, Class)} still works: {@link TestDataManager} converts
 * through a dedicated lenient {@link com.fasterxml.jackson.databind.ObjectMapper} that
 * coerces numeric/boolean strings into the target DTO's actual field types.</p>
 */
final class CsvDataReader implements TestDataReader {

    @Override
    public List<Map<String, Object>> read(String classpathResource) {
        List<Map<String, Object>> records = new ArrayList<>();
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
                new InputStreamReader(FileUtils.openClasspathResource(classpathResource), StandardCharsets.UTF_8))) {
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                records.add(TestDataReader.unflatten(row));
            }
        } catch (IOException | CsvValidationException e) {
            throw new TestDataException("Failed to read/parse CSV test data file '" + classpathResource + "'.", e);
        }
        return records;
    }
}
