package com.framework.exceptions;

/**
 * Thrown when an API call fails in a way the test should not have to
 * interpret raw REST Assured/HTTP exceptions for: a connection failure, an
 * unexpected status code where the caller asked for validation, or a
 * response body that could not be parsed into the expected type.
 */
public class ApiException extends FrameworkException {

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
