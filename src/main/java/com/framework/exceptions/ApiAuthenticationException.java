package com.framework.exceptions;

/**
 * Thrown when authentication itself fails: login/register rejected by the
 * API, or a protected endpoint was called with no token available (see
 * {@link com.framework.api.services.AuthenticationService}). Distinct from
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
