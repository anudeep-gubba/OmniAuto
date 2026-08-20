package com.framework.exceptions;

/**
 * Thrown when authentication itself fails: login/register rejected by the
 * API, or a protected endpoint was called with no token available (see an
 * application-specific auth service, e.g. {@code com.tests.application.services.AuthenticationService}
 * - application-specific, so it lives in {@code src/test}, not linkable from here). Distinct from
 * {@link ApiException} so a test can tell "the API call worked but returned
 * an unexpected result" apart from "we were never authenticated to begin
 * with."
 */
public class ApiAuthenticationException extends ApiException {

    public ApiAuthenticationException(String message) {
        super(message);
    }

    public ApiAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
