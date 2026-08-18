package com.framework.exceptions;

/**
 * Thrown for anything that goes wrong loading or reading test data: an unsupported file
 * extension, a missing classpath resource, a malformed JSON/YAML/CSV/Excel file, or a
 * request for a record name/index that does not exist in the loaded file.
 */
public class TestDataException extends FrameworkException {

    public TestDataException(String message) {
        super(message);
    }

    public TestDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
