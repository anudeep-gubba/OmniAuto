package com.framework.exceptions;

/**
 * Root of the framework's unchecked exception hierarchy.
 *
 * <p>All framework-specific exceptions (configuration, secrets, driver, test-data,
 * API, element interaction, ...) extend this instead of throwing raw
 * {@link RuntimeException} or letting library exceptions leak unwrapped, so callers
 * can catch one type and messages consistently identify what failed and why
 * (requirement.md &sect;30).</p>
 */
public class FrameworkException extends RuntimeException {

    public FrameworkException(String message) {
        super(message);
    }

    public FrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
