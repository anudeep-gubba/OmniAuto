package com.framework.utils;

import com.framework.exceptions.TestDataException;

import java.io.InputStream;

/**
 * Shared classpath-resource loading, currently just for the four
 * {@code com.framework.testdata} readers (JSON/YAML/CSV/Excel, Phase 9) - one place that
 * turns "resource not found" into a fail-fast {@link TestDataException} instead of each
 * reader handling a {@code null} {@link InputStream} its own way (RULE 5).
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * Opens {@code classpathResource} (e.g. {@code "testdata/json/login.json"}) for reading.
     * Caller is responsible for closing the returned stream (try-with-resources).
     */
    public static InputStream openClasspathResource(String classpathResource) {
        InputStream in = FileUtils.class.getClassLoader().getResourceAsStream(classpathResource);
        if (in == null) {
            throw new TestDataException(
                    "Test data file not found on the classpath: '" + classpathResource
                            + "'. Expected at src/test/resources/" + classpathResource + " (or src/main/resources).");
        }
        return in;
    }
}
