package com.framework.exceptions;

/**
 * Thrown when a secret cannot be resolved: a required key is missing from both
 * the CI/CD environment and {@code .secret.env}, or a {@code ${{KEY}}} placeholder
 * in test data cannot be resolved by any registered
 * {@link com.framework.testdata.PlaceholderSource}.
 *
 * <p>Always thrown eagerly, at the point of resolution &mdash; never leaves an
 * unresolved {@code ${{...}}} token silently in text (requirement.md &sect;14).</p>
 */
public class SecretResolutionException extends FrameworkException {

    public SecretResolutionException(String message) {
        super(message);
    }

    public SecretResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
