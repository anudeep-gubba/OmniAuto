package com.framework.exceptions;

/**
 * Thrown when configuration cannot be loaded or fails validation: an unsupported
 * environment name, a missing {@code config/{env}.properties} file, or a required
 * key that is absent/blank after the full precedence chain is applied.
 *
 * <p>Always thrown eagerly, at first configuration access — never mid-test
 * (requirement.md &sect;31, fail fast).</p>
 */
public class ConfigurationException extends FrameworkException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
